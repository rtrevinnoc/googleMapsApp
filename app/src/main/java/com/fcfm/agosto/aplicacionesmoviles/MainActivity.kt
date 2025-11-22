
package com.fcfm.agosto.aplicacionesmoviles

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.Button
import android.widget.EditText
import android.widget.NumberPicker
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import androidx.lifecycle.lifecycleScope
import com.fcfm.agosto.aplicacionesmoviles.place.Place
import com.fcfm.agosto.aplicacionesmoviles.place.PlacesReader
import com.fcfm.agosto.aplicacionesmoviles.scores.Score
import com.fcfm.agosto.aplicacionesmoviles.scores.ScoresReader
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class MainActivity : AppCompatActivity() {

    private val places = mutableListOf<Place>()
    private lateinit var auth: FirebaseAuth
    private lateinit var credentialManager: CredentialManager
    private val placesReader = PlacesReader(this)
    private val db = Firebase.firestore
    private val btnSignIn by lazy { findViewById<Button>(R.id.signIn) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        auth = FirebaseAuth.getInstance()
        credentialManager = CredentialManager.create(this)

        actualizarBotonUsuario()

        btnSignIn.setOnClickListener {
            if (auth.currentUser != null) {
                startActivity(Intent(this, DetalleDeUsuario::class.java))
            } else {
                signIn()
            }
        }

        btnSignIn.setOnLongClickListener {
            if (auth.currentUser != null) {
                cerrarSesion()
                true
            } else false
        }

        loadPlacesAndMap()
    }

    override fun onResume() {
        super.onResume()
        actualizarBotonUsuario()
    }

    private fun actualizarBotonUsuario() {
        val user = auth.currentUser
        btnSignIn.text = when {
            !user?.displayName.isNullOrBlank() -> user?.displayName!!
            user?.email != null -> user.email!!.substringBefore("@")
            user != null -> "Mi cuenta"
            else -> "Iniciar sesión"
        }
    }

    private fun cerrarSesion() {
        auth.signOut()
        lifecycleScope.launch {
            try {
                credentialManager.clearCredentialState(ClearCredentialStateRequest())
            } catch (_: Exception) {}
        }
        actualizarBotonUsuario()
        mostrarMensaje("Sesión cerrada")
    }

    private fun loadPlacesAndMap() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                places.clear()
                places.addAll(placesReader.read())
                withContext(Dispatchers.Main) { setupMap() }
            } catch (_: Exception) {}
        }
    }

    private fun setupMap() {
        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as? SupportMapFragment
        mapFragment?.getMapAsync { map ->

            // === OPTIMIZACIONES HUAWEI ===
            map.mapType = GoogleMap.MAP_TYPE_NORMAL
            map.uiSettings.apply {
                isMapToolbarEnabled = false
                isMyLocationButtonEnabled = false
                isCompassEnabled = false
                isRotateGesturesEnabled = true
                isTiltGesturesEnabled = true
                isZoomControlsEnabled = true  // útil si no tienes botón de ubicación
            }
            try {
                map.isBuildingsEnabled = false
                map.isTrafficEnabled = false
                map.isIndoorEnabled = false
            } catch (ignored: Exception) {}

            // === CENTRO EN MÉXICO (FCFM, UNAM, CDMX) EN VEZ DE ESTADOS UNIDOS ===
            val fcfm = com.google.android.gms.maps.model.LatLng(19.3317, -99.1844)  // Coordenadas exactas de la FCFM
            val cameraUpdate = com.google.android.gms.maps.CameraUpdateFactory.newLatLngZoom(fcfm, 14.5f)

            map.setOnMapLoadedCallback {
                map.moveCamera(cameraUpdate)  // primera vez suave
                addMarkers(map)
            }

            // Si por alguna razón tarda mucho en cargar (Huawei...), forzamos el centro
            mapFragment.view?.postDelayed({
                if (map.cameraPosition.zoom < 5f) {  // si sigue en zoom 0~1 → no cargó
                    map.animateCamera(cameraUpdate, 1000, null)
                    addMarkers(map)
                    mostrarMensaje("Mostrando Ciudad de México")
                }
            }, 8000)

            // === LONG CLICK → NUEVO LUGAR ===
            map.setOnMapLongClickListener { latLng ->
                val view = LayoutInflater.from(this).inflate(R.layout.new_place_form, null)
                val etTitle = view.findViewById<EditText>(R.id.new_title)
                val etAddress = view.findViewById<EditText>(R.id.new_address)
                val npRating = view.findViewById<NumberPicker>(R.id.new_rating).apply {
                    minValue = 0; maxValue = 5; value = 3
                }

                AlertDialog.Builder(this)
                    .setTitle("Nuevo lugar")
                    .setView(view)
                    .setPositiveButton("Agregar") { _, _ ->
                        placesReader.addPlace(
                            etTitle.text.toString().ifBlank { "Lugar nuevo" },
                            latLng,
                            etAddress.text.toString().ifBlank { "Sin dirección" },
                            npRating.value.toFloat()
                        )
                        recargarMarcadores(map)
                        mostrarMensaje("Lugar agregado")
                    }
                    .setNegativeButton("Cancelar", null)
                    .show()
            }

            // === CLICK EN MARCADOR → CALIFICAR ===
            map.setOnMarkerClickListener { marker ->
                val place = marker.tag as? Place ?: return@setOnMarkerClickListener false
                val view = LayoutInflater.from(this).inflate(R.layout.marker_popup, null)

                view.findViewById<TextView>(R.id.marker_popup_title).text = place.name
                view.findViewById<TextView>(R.id.marker_popup_address).text = place.address
                val npRating = view.findViewById<NumberPicker>(R.id.marker_popup_rating).apply {
                    minValue = 0; maxValue = 5; value = place.rating.toInt().coerceIn(0, 5)
                }

                AlertDialog.Builder(this)
                    .setTitle(place.name)
                    .setView(view)
                    .setPositiveButton("Guardar calificación") { _, _ ->
                        if (auth.currentUser == null) {
                            AlertDialog.Builder(this)
                                .setMessage("Inicia sesión para calificar")
                                .setPositiveButton("Iniciar sesión") { _, _ -> signIn() }
                                .setNegativeButton("Cancelar", null)
                                .show()
                            return@setPositiveButton
                        }

                        val nuevaCal = npRating.value.toFloat()
                        val fecha = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                            .apply { timeZone = TimeZone.getTimeZone("UTC") }
                            .format(Date())

                        val score = Score(place.id, auth.currentUser!!.email!!, nuevaCal, fecha)

                        lifecycleScope.launch(Dispatchers.IO) {
                            try {
                                val promedio = ScoresReader(this@MainActivity).doPost(score)
                                place.rating = promedio
                                db.collection("places").document(place.id).set(place)

                                withContext(Dispatchers.Main) {
                                    recargarMarcadores(map)
                                    mostrarMensaje("Calificación guardada (promedio: $promedio)")
                                }
                            } catch (e: Exception) {
                                withContext(Dispatchers.Main) {
                                    mostrarMensaje("Error al guardar")
                                }
                            }
                        }
                    }
                    .setNegativeButton("Cancelar", null)
                    .show()

                true
            }

            // Marcadores iniciales (se vuelven a poner después del moveCamera)
            addMarkers(map)
        }
    }

    private fun recargarMarcadores(map: GoogleMap) {
        lifecycleScope.launch(Dispatchers.IO) {
            places.clear()
            places.addAll(placesReader.read())
            withContext(Dispatchers.Main) { addMarkers(map) }
        }
    }

    private fun addMarkers(map: GoogleMap) {
        map.clear()
        val hayVoto = auth.currentUser != null

        places.forEach { place ->
            val color = if (place.rating > 0f && hayVoto)
                BitmapDescriptorFactory.HUE_GREEN
            else
                BitmapDescriptorFactory.HUE_RED

            map.addMarker(
                MarkerOptions()
                    .title(place.name)
                    .position(place.latLng)
                    .icon(BitmapDescriptorFactory.defaultMarker(color))
            )?.tag = place
        }
    }

    private fun mostrarMensaje(texto: String) {
        com.google.android.material.snackbar.Snackbar.make(
            findViewById(android.R.id.content),
            texto,
            com.google.android.material.snackbar.Snackbar.LENGTH_SHORT
        ).show()
    }

    private fun signInWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    actualizarBotonUsuario()
                    val nombre = auth.currentUser?.displayName?.split(" ")?.getOrNull(0) ?: "amigo"
                    mostrarMensaje("¡Bienvenido, $nombre!")
                }
            }
    }

    private fun signIn() {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val googleIdOption = GetGoogleIdOption.Builder()
                    .setFilterByAuthorizedAccounts(false)
                    .setServerClientId(getString(R.string.default_web_client_id))
                    .build()

                val request = GetCredentialRequest.Builder()
                    .addCredentialOption(googleIdOption)
                    .build()

                val result = credentialManager.getCredential(this@MainActivity, request)
                val token = GoogleIdTokenCredential.createFrom(result.credential.data)
                signInWithGoogle(token.idToken)
            } catch (_: GetCredentialException) {
                mostrarMensaje("Error al iniciar sesión")
            }
        }
    }
}
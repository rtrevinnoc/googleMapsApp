package com.google.codelabs.buildyourfirstmap

import androidx.core.content.ContextCompat

import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.NumberPicker
import android.widget.TextView
import android.widget.Toast
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.lifecycle.lifecycleScope

import kotlinx.coroutines.CoroutineScope

import android.view.Gravity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.tasks.await
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.Credential
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.provider.PendingIntentHandler
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential

import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.codelabs.buildyourfirstmap.place.Place
import com.google.codelabs.buildyourfirstmap.place.PlacesReader
import com.google.codelabs.buildyourfirstmap.scores.Score
import com.google.codelabs.buildyourfirstmap.scores.ScoresReader
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var mMap: GoogleMap
    private val places: MutableList<Place> = mutableListOf()
    private lateinit var auth: FirebaseAuth
    private lateinit var credentialManager: CredentialManager
    private var user: FirebaseUser? = null
    private lateinit var placesReader: PlacesReader
    private val db = Firebase.firestore
    private lateinit var btnSignIn: MaterialButton
    private lateinit var btnMapType: FloatingActionButton
    private val LOCATION_PERMISSION_REQUEST_CODE = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FirebaseApp.initializeApp(this)
        setContentView(R.layout.activity_main)

        auth = FirebaseAuth.getInstance()
        credentialManager = CredentialManager.create(this)
        placesReader = PlacesReader(this)

        btnSignIn = findViewById(R.id.btnSignIn)
        btnMapType = findViewById(R.id.btnMapType)


        updateSignInButton()
        btnSignIn.setOnClickListener {
            if (auth.currentUser != null) {

                startActivity(Intent(this, DetalleDeUsuario::class.java))
            } else {

                signIn()
            }
        }

        // Carga mapa
        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)
    }

    override fun onResume() {
        super.onResume()
        updateSignInButton()
    }

    private fun updateSignInButton() {
        if (auth.currentUser != null) {
            btnSignIn.text = ""
            btnSignIn.icon = ContextCompat.getDrawable(this, R.drawable.baseline_person_24)
            btnSignIn.setPadding(35, 0, 0, 0)
            btnSignIn.layoutParams.width = 120
            btnSignIn.layoutParams.height = 120

        } else {
            btnSignIn.text = "Iniciar Sesión"
            btnSignIn.icon = ContextCompat.getDrawable(this, R.drawable.baseline_person_24)
            btnSignIn.setBackgroundResource(R.drawable.btn_default)

        }
    }


    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        mMap.uiSettings.isZoomControlsEnabled = true
        mMap.uiSettings.isCompassEnabled = true
        mMap.uiSettings.isMapToolbarEnabled = true

        enableMyLocation()
        loadPlaces()




        btnMapType.setOnClickListener {
            mMap.mapType = when (mMap.mapType) {
                GoogleMap.MAP_TYPE_NORMAL -> GoogleMap.MAP_TYPE_SATELLITE
                GoogleMap.MAP_TYPE_SATELLITE -> GoogleMap.MAP_TYPE_HYBRID
                GoogleMap.MAP_TYPE_HYBRID -> GoogleMap.MAP_TYPE_TERRAIN
                else -> GoogleMap.MAP_TYPE_NORMAL
            }
            val typeNames = mapOf(
                GoogleMap.MAP_TYPE_NORMAL to "Normal",
                GoogleMap.MAP_TYPE_SATELLITE to "Satélite",
                GoogleMap.MAP_TYPE_HYBRID to "Híbrido",
                GoogleMap.MAP_TYPE_TERRAIN to "Terreno"
            )
            Toast.makeText(this, "Mapa: ${typeNames[mMap.mapType]}", Toast.LENGTH_SHORT).show()
        }

        // Click largo para crear lugar
        mMap.setOnMapLongClickListener { latLng ->
            val view = LayoutInflater.from(this).inflate(R.layout.new_place_form, null)
            val newTitle = view.findViewById<EditText>(R.id.new_title)
            val newAddress = view.findViewById<EditText>(R.id.new_address)
            val newRatingSelector = view.findViewById<NumberPicker>(R.id.new_rating)
            newRatingSelector.maxValue = 5
            newRatingSelector.minValue = 0
            newRatingSelector.value = 3

            AlertDialog.Builder(this)
                .setTitle("Nuevo Lugar")
                .setView(view)
                .setPositiveButton("Agregar") { _, _ ->
                    val title = newTitle.text.toString().trim()
                    val address = newAddress.text.toString().trim()
                    val rating = newRatingSelector.value.toFloat()

                    // Validar que el título no esté vacío
                    if (title.isBlank()) {
                        Toast.makeText(this, "El nombre del lugar es requerido", Toast.LENGTH_SHORT).show()
                        return@setPositiveButton
                    }

                    // Agregar lugar y esperar la operación
                    lifecycleScope.launch(Dispatchers.IO) {
                        val placeId = placesReader.addPlace(title, latLng, address, rating)

                        withContext(Dispatchers.Main) {
                            if (placeId != null) {
                                Toast.makeText(this@MainActivity, "Lugar agregado: $title", Toast.LENGTH_SHORT).show()

                                loadPlaces()
                            } else {
                                Toast.makeText(this@MainActivity, "Error al agregar el lugar", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
                .setNegativeButton("Cancelar", null)
                .show()
        }


        mMap.setOnInfoWindowClickListener { marker ->
            val place = marker.tag as? Place ?: return@setOnInfoWindowClickListener
            showPlaceDetailsDialog(place, marker)
        }
    }

    //Se crea el lugar en firestore
    private fun loadPlaces() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val placesList = placesReader.read()
                places.clear()
                places.addAll(placesList)

                withContext(Dispatchers.Main) {
                    mMap.clear()
                    addMarkers()

                    // Ajuste de camara
                    if (places.isNotEmpty()) {
                        val boundsBuilder = LatLngBounds.builder()
                        places.forEach { boundsBuilder.include(it.latLng) }
                        val bounds = boundsBuilder.build()
                        mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 100))
                    }
                }
            } catch (e: Exception) {
                Log.e("Firestore", "Error loading places", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Error al cargar lugares", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun addMarkers() {
        places.forEach { place ->
            val marker = mMap.addMarker(
                MarkerOptions()
                    .title(place.name)
                    .position(place.latLng)
                    .snippet(place.address)
            )
            marker?.tag = place
        }
    }


    private fun showPlaceDetailsDialog(place: Place, marker: Marker) {
        val view = LayoutInflater.from(this).inflate(R.layout.marker_popup, null)
        val title = view.findViewById<TextView>(R.id.marker_popup_title)
        title.text = place.name
        val address = view.findViewById<TextView>(R.id.marker_popup_address)
        address.text = place.address
        val ratingText = view.findViewById<TextView>(R.id.marker_popup_rating)
        ratingText.text = "Puntuación promedio: ${if (place.rating > 0) String.format("%.1f", place.rating) else "-"}"

        val dialog = AlertDialog.Builder(this)
            .setTitle("Información del Lugar")
            .setView(view)
            .setPositiveButton("Calificar") { _, _ ->
                showRatingDialog(place, marker)
            }
            .setNeutralButton("Eliminar") { _, _ ->
                showDeleteConfirmDialog(place, marker)
            }
            .setNegativeButton("Cerrar", null)
            .create()

        dialog.show()


        val window = dialog.window
        window?.let {
            val params = it.attributes
            params.gravity = Gravity.TOP or Gravity.END

            params.x = 20
            params.y = 100

            it.attributes = params
        }
    }

    //Calificar un lugar
    private fun showRatingDialog(place: Place, marker: Marker) {
        val view = LayoutInflater.from(this).inflate(R.layout.rating_dialog, null)
        val ratingSelector = view.findViewById<NumberPicker>(R.id.rating_picker)
        ratingSelector.maxValue = 5
        ratingSelector.minValue = 0
        ratingSelector.value = place.rating.toInt()

        AlertDialog.Builder(this)
            .setTitle("Calificar ${place.name}")
            .setView(view)
            .setPositiveButton("Guardar") { _, _ ->
                val rating = ratingSelector.value.toFloat()

                if (auth.currentUser == null) {
                    Toast.makeText(this, "Debes iniciar sesión para calificar", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val currentTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                currentTime.timeZone = TimeZone.getTimeZone("UTC")
                val currentTimeInUTC = currentTime.format(Date())
                val score = Score(place.id, auth.currentUser?.email!!, rating, currentTimeInUTC)

                lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        val scoresReader = ScoresReader(this@MainActivity)
                        val newScore = scoresReader.doPost(score)

                        withContext(Dispatchers.Main) {
                            place.rating = newScore
                            // Usar update en lugar de set para solo actualizar el rating
                            db.collection(getString(R.string.placesFirestore))
                                .document(place.id)
                                .update("rating", newScore)
                            Toast.makeText(this@MainActivity, "Gracias por calificar ⭐ $rating", Toast.LENGTH_SHORT).show()
                            marker.showInfoWindow()
                            loadPlaces()
                        }
                    } catch (e: Exception) {
                        Log.e("Rating", "Error al enviar calificación", e)
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@MainActivity, "Error al calificar", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }


    private fun showDeleteConfirmDialog(place: Place, marker: Marker) {
        AlertDialog.Builder(this)
            .setTitle("Eliminar lugar")
            .setMessage("¿Estás seguro de que deseas eliminar \"${place.name}\"?\n\nEsta acción no se puede deshacer.")
            .setPositiveButton("Eliminar") { _, _ ->
                lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        val deleted = placesReader.deletePlace(place.id)
                        withContext(Dispatchers.Main) {
                            if (deleted) {
                                marker.remove()
                                places.remove(place)
                                Toast.makeText(this@MainActivity, "Lugar eliminado: ${place.name}", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(this@MainActivity, "Error al eliminar el lugar", Toast.LENGTH_SHORT).show()
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("MainActivity", "Error al eliminar lugar", e)
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@MainActivity, "Error al eliminar el lugar", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }


    private fun enableMyLocation() {
        if (ActivityCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            mMap.isMyLocationEnabled = true
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                LOCATION_PERMISSION_REQUEST_CODE
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                enableMyLocation()
            } else {
                Toast.makeText(this, "Permiso de ubicación denegado", Toast.LENGTH_SHORT).show()
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
                handleSignInResult(result)
            } catch (e: GetCredentialCancellationException) {
                Toast.makeText(this@MainActivity, "Inicio de sesión cancelado", Toast.LENGTH_SHORT).show()
            } catch (e: GetCredentialException) {
                Log.w("AUTH", "Credential error", e)
                Toast.makeText(this@MainActivity, "Error al iniciar sesión", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun handleSignInResult(result: GetCredentialResponse) {
        val credential = result.credential
        val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data)
        signInWithGoogle(googleIdToken.idToken)
    }

    private fun signInWithGoogle(tokenId: String) {
        val credential = GoogleAuthProvider.getCredential(tokenId, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    user = auth.currentUser
                    Log.i("AUTH", "Google Sign-In successful: ${user?.email}")
                    Toast.makeText(this, "Bienvenido, ${user?.email}", Toast.LENGTH_SHORT).show()
                    updateSignInButton()
                    startActivity(Intent(this, DetalleDeUsuario::class.java))
                } else {
                    Log.w("AUTH", "Google Sign-In failed", task.exception)
                    Toast.makeText(this, "Error al autenticar", Toast.LENGTH_SHORT).show()
                }
            }
    }
}
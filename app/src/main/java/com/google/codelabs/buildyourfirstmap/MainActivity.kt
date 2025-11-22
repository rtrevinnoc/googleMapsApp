package com.google.codelabs.buildyourfirstmap

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
    private lateinit var btnReload: FloatingActionButton

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
        btnReload = findViewById(R.id.btnReload)

        // Configurar botón de Sign In
        updateSignInButton()
        btnSignIn.setOnClickListener {
            if (auth.currentUser != null) {
                // Si ya está logueado, ir a detalles de usuario
                startActivity(Intent(this, DetalleDeUsuario::class.java))
            } else {
                // Si no está logueado, hacer sign in
                signIn()
            }
        }

        // Cargar mapa
        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)
    }

    override fun onResume() {
        super.onResume()
        updateSignInButton()
    }

    private fun updateSignInButton() {
        if (auth.currentUser != null) {
            btnSignIn.text = "Ver Perfil"
            btnSignIn.setIconResource(android.R.drawable.ic_menu_myplaces)
        } else {
            btnSignIn.text = "Iniciar Sesión"
            btnSignIn.setIconResource(android.R.drawable.ic_menu_preferences)
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        mMap.uiSettings.isZoomControlsEnabled = true
        mMap.uiSettings.isCompassEnabled = true
        mMap.uiSettings.isMapToolbarEnabled = true

        enableMyLocation()
        loadPlaces()

        // --- Botón recargar lugares ---
        btnReload.setOnClickListener { loadPlaces() }

        // --- Cambiar tipo de mapa ---
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

        // --- Click largo para CREAR nuevo lugar ---
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
                    val title = newTitle.text.toString().ifBlank { "Sin nombre" }
                    val address = newAddress.text.toString().ifBlank { "Sin dirección" }
                    val rating = newRatingSelector.value.toFloat()

                    placesReader.addPlace(title, latLng, address, rating)

                    Toast.makeText(this, "Lugar agregado: $title", Toast.LENGTH_SHORT).show()
                    loadPlaces()
                }
                .setNegativeButton("Cancelar", null)
                .show()
        }

        // --- Click en marcador para VER/EDITAR lugar ---
        mMap.setOnInfoWindowClickListener { marker ->
            val place = marker.tag as? Place ?: return@setOnInfoWindowClickListener
            showPlaceDetailsDialog(place, marker)
        }
    }

    // --- Cargar lugares desde Firestore ---
    private fun loadPlaces() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val placesList = placesReader.read()
                places.clear()
                places.addAll(placesList)

                withContext(Dispatchers.Main) {
                    mMap.clear()
                    addMarkers()

                    // Ajustar cámara si hay lugares
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

    // --- Diálogo de detalles y edición de lugar ---
    private fun showPlaceDetailsDialog(place: Place, marker: Marker) {
        val view = LayoutInflater.from(this).inflate(R.layout.marker_popup, null)
        val title = view.findViewById<TextView>(R.id.marker_popup_title)
        title.text = place.name
        val address = view.findViewById<TextView>(R.id.marker_popup_address)
        address.text = place.address
        val ratingSelector = view.findViewById<NumberPicker>(R.id.marker_popup_rating)
        ratingSelector.maxValue = 5
        ratingSelector.minValue = 0
        ratingSelector.value = place.rating.toInt()

        AlertDialog.Builder(this)
            .setTitle("Información del Lugar")
            .setView(view)
            .setPositiveButton("Calificar") { _, _ ->
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
                            db.collection(getString(R.string.placesFirestore)).document(place.id).set(place)
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
            .setNegativeButton("Cerrar", null)
            .show()
    }

    // --- Habilitar ubicación del usuario ---
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

    // --- Autenticación con Google ---
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
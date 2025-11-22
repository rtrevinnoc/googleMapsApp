package com.google.codelabs.buildyourfirstmap

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.lifecycle.lifecycleScope

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
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.codelabs.buildyourfirstmap.place.Place
import com.google.codelabs.buildyourfirstmap.place.PlacesReader
import com.google.codelabs.buildyourfirstmap.scores.Score
import com.google.codelabs.buildyourfirstmap.scores.ScoresReader
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var mMap: GoogleMap
    private lateinit var auth: FirebaseAuth
//    private lateinit var googleSignInClient: GoogleSignInClient

    private lateinit var credentialManager: CredentialManager
    private lateinit var googleIdOption: GetSignInWithGoogleOption

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
        googleIdOption = GetSignInWithGoogleOption.Builder(
            getString(R.string.default_web_client_id)
        ).build()

        btnSignIn = findViewById(R.id.btnSignIn)
        btnMapType = findViewById(R.id.btnMapType)
        btnReload = findViewById(R.id.btnReload)

        btnSignIn.setOnClickListener { signInWithGoogleNew() }

        // Cargar mapa
        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)
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
        }

        // --- Listener de clic en marcador ---
        mMap.setOnInfoWindowClickListener { marker ->
            val place = marker.tag as? Place ?: return@setOnInfoWindowClickListener
            showRatingDialog(place, marker)
        }
    }

    // --- Cargar lugares desde Firestore ---
    private fun loadPlaces() {
        lifecycleScope.launch {
            try {
                val places = PlacesReader(this@MainActivity).read()
                mMap.clear()

                for (place in places) {
                    val marker = mMap.addMarker(
                        MarkerOptions()
                            .position(place.latLng)
                            .title(place.name)
                            .snippet(place.address)
                    )
                    marker?.tag = place
                }

                if (places.isNotEmpty()) {
                    val boundsBuilder = LatLngBounds.builder()
                    for (place in places) boundsBuilder.include(place.latLng)
                    val bounds = boundsBuilder.build()
                    mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 100))
                }

                mMap.setInfoWindowAdapter(MarkerPopupAdapter(this@MainActivity, lifecycleScope))
            } catch (e: Exception) {
                Log.e("FirebasePlaces", "Error al cargar lugares", e)
                Toast.makeText(this@MainActivity, "Error al cargar lugares", Toast.LENGTH_SHORT).show()
            }
        }
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

    private fun signInWithGoogleNew() {
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        lifecycleScope.launch {
            try {
                val result = credentialManager.getCredential(
                    request = request,
                    context = this@MainActivity
                )
                handleSignInResult(result)
            } catch (e: GetCredentialCancellationException) {
                Toast.makeText(this@MainActivity, "Inicio cancelado", Toast.LENGTH_SHORT).show()
            } catch (e: GetCredentialException) {
                Log.e("CredentialManager", "Error: ${e.message}")
                Toast.makeText(this@MainActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun handleSignInResult(result: GetCredentialResponse) {
        val credential = result.credential
        if (credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            val googleCredential = GoogleIdTokenCredential.createFrom(credential.data)
            val idToken = googleCredential.idToken
            val firebaseCredential = GoogleAuthProvider.getCredential(idToken, null)

            auth.signInWithCredential(firebaseCredential)
                .addOnCompleteListener(this) { task ->
                    if (task.isSuccessful) {
                        val user = auth.currentUser
                        Toast.makeText(this, "Bienvenido, ${user?.email}", Toast.LENGTH_SHORT).show()
                        btnSignIn.visibility = View.GONE
                    } else {
                        Toast.makeText(this, "Error al autenticar", Toast.LENGTH_SHORT).show()
                    }
                }
        }
    }


    // --- Diálogo de calificación ---
    private fun showRatingDialog(place: Place, marker: Marker) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Calificar ${place.name}")

        val options = arrayOf("1 ★", "2 ★★", "3 ★★★", "4 ★★★★", "5 ★★★★★")
        builder.setItems(options) { _, which ->
            val rating = (which + 1).toFloat()

            if (auth.currentUser == null) {
                Toast.makeText(this, "Debes iniciar sesión para calificar", Toast.LENGTH_SHORT).show()
                return@setItems
            }

            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                .apply { timeZone = TimeZone.getTimeZone("UTC") }
                .format(Date())

            val score = Score(
                placeId = place.id ?: place.name,
                username = auth.currentUser?.email ?: "anónimo",
                score = rating,
                timestamp = timestamp
            )

            Thread {
                try {
                    val newAvg = ScoresReader(this).doPost(score)
                    runOnUiThread {
                        Toast.makeText(this, "Gracias por calificar ⭐ $rating", Toast.LENGTH_SHORT).show()
                        marker.showInfoWindow()
                    }
                } catch (e: Exception) {
                    Log.e("Rating", "Error al enviar calificación", e)
                }
            }.start()
        }

        builder.setNegativeButton("Cancelar", null)
        builder.show()
    }
}

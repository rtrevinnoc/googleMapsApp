package com.google.codelabs.buildyourfirstmap

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Toast
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import android.location.Geocoder
import java.util.Locale
import java.util.UUID

// Importaciones CORRECTAS de Firebase
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider

// Importaciones para Google Sign-In
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.maps.model.Marker
import androidx.appcompat.app.AlertDialog

// Data class simple
data class Place(
    val id: String = "",
    val name: String = "",
    val address: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val rating: Double = 0.0,
    val totalRatings: Int = 0,
    val userId: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

class MainActivity : AppCompatActivity(), OnMapReadyCallback, GoogleMap.OnMarkerClickListener {

    private lateinit var mMap: GoogleMap
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private lateinit var googleSignInClient: GoogleSignInClient
    private val markers = mutableMapOf<String, Marker>()

    private val signInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        try {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            val account = task.getResult(ApiException::class.java)
            val credential = GoogleAuthProvider.getCredential(account?.idToken, null)

            auth.signInWithCredential(credential)
                .addOnCompleteListener { authTask ->
                    if (authTask.isSuccessful) {
                        Toast.makeText(this, "Inicio de sesión exitoso", Toast.LENGTH_SHORT).show()
                        loadFirebaseMarkers()
                    } else {
                        Toast.makeText(this, "Error en login: ${authTask.exception?.message}", Toast.LENGTH_LONG).show()
                    }
                }
        } catch (e: ApiException) {
            Toast.makeText(this, "Error: ${e.statusCode}", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Configurar Google Sign-In
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken("766813132621-xxxxxxxxxxxxxxxxxxxxxxxxxxxx.apps.googleusercontent.com")
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)

        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        mMap.setOnMarkerClickListener(this)

        // Configurar adaptador de popup personalizado
        mMap.setInfoWindowAdapter(MarkerPopupAdapter(this))

        val monterrey = LatLng(25.6866, -100.3161)
        mMap.addMarker(MarkerOptions().position(monterrey).title("Marcador en Monterrey"))
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(monterrey, 10f))

        // Verificar si ya está autenticado
        if (auth.currentUser != null) {
            loadFirebaseMarkers()
        } else {
            // Iniciar autenticación automáticamente
            startGoogleSignIn()
        }

        // Evento de clic en el mapa
        mMap.setOnMapClickListener { latLng ->
            if (auth.currentUser == null) {
                Toast.makeText(this, "Inicia sesión para agregar marcadores", Toast.LENGTH_LONG).show()
                startGoogleSignIn()
                return@setOnMapClickListener
            }
            addNewPlaceToFirebase(latLng)
        }
    }

    private fun startGoogleSignIn() {
        val signInIntent = googleSignInClient.signInIntent
        signInLauncher.launch(signInIntent)
    }

    private fun loadFirebaseMarkers() {
        // Escuchar cambios en tiempo real de Firestore
        db.collection("places")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Toast.makeText(this, "Error cargando marcadores", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }

                // Limpiar marcadores existentes
                markers.values.forEach { it.remove() }
                markers.clear()

                snapshot?.documents?.forEach { document ->
                    val place = document.toObject(Place::class.java)
                    if (place != null) {
                        addMarkerToMap(place)
                    }
                }
            }
    }

    private fun addNewPlaceToFirebase(latLng: LatLng) {
        val geocoder = Geocoder(this, Locale.getDefault())
        val address = try {
            val resultado = geocoder.getFromLocation(latLng.latitude, latLng.longitude, 1)
            if (resultado != null && resultado.isNotEmpty()) {
                resultado[0].getAddressLine(0)
            } else {
                "Dirección desconocida"
            }
        } catch (e: Exception) {
            "Error al obtener dirección"
        }

        val user = auth.currentUser
        val newPlace = Place(
            id = UUID.randomUUID().toString(),
            name = "Lugar Personalizado",
            address = address,
            latitude = latLng.latitude,
            longitude = latLng.longitude,
            rating = 0.0,
            userId = user?.uid ?: "unknown"
        )

        // Guardar en Firestore
        db.collection("places").document(newPlace.id)
            .set(newPlace)
            .addOnSuccessListener {
                Toast.makeText(this, "Marcador guardado en la nube", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error al guardar: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun addMarkerToMap(place: Place) {
        val position = LatLng(place.latitude, place.longitude)
        val marker = mMap.addMarker(
            MarkerOptions()
                .position(position)
                .title(place.name)
                .snippet("Rating: ${place.rating} ★")
        )

        marker?.tag = place
        if (marker != null) {
            markers[place.id] = marker
        }
    }

    override fun onMarkerClick(marker: Marker): Boolean {
        val place = marker.tag as? Place ?: return false

        // Mostrar diálogo de calificación
        showRatingDialog(place)
        return true
    }

    private fun showRatingDialog(place: Place) {
        if (auth.currentUser == null) {
            Toast.makeText(this, "Inicia sesión para calificar", Toast.LENGTH_LONG).show()
            startGoogleSignIn()
            return
        }

        val ratingOptions = arrayOf("1 ★", "2 ★", "3 ★", "4 ★", "5 ★")

        AlertDialog.Builder(this)
            .setTitle("Calificar ${place.name}")
            .setItems(ratingOptions) { dialog, which ->
                val rating = (which + 1).toDouble()
                ratePlaceInFirebase(place, rating)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun ratePlaceInFirebase(place: Place, newRating: Double) {
        // Actualizar rating en Firebase usando transacción
        val placeRef = db.collection("places").document(place.id)

        db.runTransaction { transaction ->
            val snapshot = transaction.get(placeRef)
            val currentPlace = snapshot.toObject(Place::class.java)
            if (currentPlace != null) {
                val updatedTotalRatings = currentPlace.totalRatings + 1
                val updatedRating = ((currentPlace.rating * currentPlace.totalRatings) + newRating) / updatedTotalRatings

                val updatedPlace = currentPlace.copy(
                    rating = updatedRating,
                    totalRatings = updatedTotalRatings
                )
                transaction.set(placeRef, updatedPlace)
            }
        }.addOnSuccessListener {
            Toast.makeText(this, "¡Gracias por tu calificación!", Toast.LENGTH_SHORT).show()
        }.addOnFailureListener { e ->
            Toast.makeText(this, "Error al calificar: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
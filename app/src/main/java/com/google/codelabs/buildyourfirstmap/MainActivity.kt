/*Manuel Acosta*/

package com.google.codelabs.buildyourfirstmap

import com.google.codelabs.buildyourfirstmap.place.PlacesRepository
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.os.Bundle
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.codelabs.buildyourfirstmap.place.Place
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var mMap: GoogleMap
    private val auth = FirebaseAuth.getInstance()
    private val repository = `PlacesRepository`()

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) enableMyLocation()
        else Toast.makeText(this, "Permiso de ubicación denegado", Toast.LENGTH_SHORT).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Autenticación anónima
        if (auth.currentUser == null) {
            auth.signInAnonymously().addOnFailureListener {
                Toast.makeText(this, "Error iniciando sesión: ${it.message}", Toast.LENGTH_LONG)
                    .show()
            }
        }

        // Cargar mapa
        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        setupInfoWindowClick()

        mMap.setOnMapLongClickListener { latLng -> showAddPlaceDialog(latLng) }

        checkLocationPermissionAndEnable()
        loadPlaces()
    }

    // ----------- UBICACIÓN -----------
    private fun checkLocationPermissionAndEnable() {
        when {
            ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED -> enableMyLocation()

            else -> requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    private fun enableMyLocation() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            mMap.isMyLocationEnabled = true
            val fused = LocationServices.getFusedLocationProviderClient(this)
            fused.lastLocation.addOnSuccessListener { location ->
                val pos = if (location != null)
                    LatLng(location.latitude, location.longitude)
                else
                    LatLng(25.6866, -100.3161) // Monterrey

                mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(pos, 12f))
            }
        }
    }

    // ----------- AGREGAR LUGAR -----------
    private fun showAddPlaceDialog(latLng: LatLng) {
        val editText = EditText(this)
        editText.hint = "Nombre del lugar (ej. Restaurante)"
        AlertDialog.Builder(this)
            .setTitle("Guardar lugar")
            .setMessage("Ingresa un nombre para este lugar")
            .setView(editText)
            .setPositiveButton("Guardar") { _, _ ->
                val name = editText.text.toString().ifBlank { "Lugar sin nombre" }
                val address = getAddressFromLatLng(latLng) ?: ""
                val place = Place(
                    name = name,
                    address = address,
                    rating = 0f,
                    lat = latLng.latitude,
                    lng = latLng.longitude
                )
                savePlace(place)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun getAddressFromLatLng(latLng: LatLng): String? {
        return try {
            val geo = Geocoder(this)
            val list = geo.getFromLocation(latLng.latitude, latLng.longitude, 1)
            list?.firstOrNull()?.getAddressLine(0)
        } catch (e: Exception) {
            null
        }
    }

    // ----------- FIRESTORE -----------
    private fun savePlace(place: Place) {
        lifecycleScope.launch {
            try {
                val uid = auth.currentUser?.uid
                repository.savePlace(place, uid)
                Toast.makeText(this@MainActivity, "Lugar guardado", Toast.LENGTH_SHORT).show()
                addMarkerForPlace(place)
            } catch (e: Exception) {
                Toast.makeText(
                    this@MainActivity,
                    "Error al guardar: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun loadPlaces() {
        lifecycleScope.launch {
            try {
                val uid = auth.currentUser?.uid
                val places = repository.getPlaces(uid)
                for (place in places) addMarkerForPlace(place)
            } catch (e: Exception) {
                Toast.makeText(
                    this@MainActivity,
                    "Error leyendo lugares: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    // ---------- MARCADORES Y COMPARTIR ----------
    private fun addMarkerForPlace(place: Place) {
        val marker = mMap.addMarker(
            MarkerOptions()
                .position(place.latLng)
                .title(place.name)
        )
        marker?.tag = place          // asociamos el objeto Place al marcador
        marker?.showInfoWindow()     // abrir el info window automáticamente
    }

    // Listener para cuando se pulsa el info window
    private fun setupInfoWindowClick() {
        mMap.setOnInfoWindowClickListener { marker ->
            val place = marker.tag as? Place
            place?.let { sharePlace(it) }
        }
    }

    // ---------- COMPARTIR ----------
    private fun sharePlace(place: Place) {
        val uri = "https://www.google.com/maps/search/?api=1&query=${place.lat},${place.lng}"
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Lugar: ${place.name}")
            putExtra(Intent.EXTRA_TEXT, "${place.name}\n${place.address}\n$uri")
        }
        startActivity(Intent.createChooser(intent, "Compartir lugar"))
    }
}

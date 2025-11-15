/*Manuel Acosta*/

package com.google.codelabs.buildyourfirstmap

import com.google.codelabs.buildyourfirstmap.place.PlacesRepository
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.os.Bundle
import android.widget.Button
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
import com.google.firebase.firestore.FirebaseFirestore
import com.google.codelabs.buildyourfirstmap.place.Place
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var mMap: GoogleMap
    private val auth = FirebaseAuth.getInstance()
    private val repository = PlacesRepository(FirebaseFirestore.getInstance())

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) enableMyLocation()
        else Toast.makeText(this, "Permiso de ubicación denegado", Toast.LENGTH_SHORT).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Verifica si hay usuario logueado
        if (FirebaseAuth.getInstance().currentUser == null) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        setContentView(R.layout.activity_main)

        // Cargar mapa
        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        // Botón de cerrar sesión
        val logoutBtn = findViewById<Button>(R.id.btnLogout)
        logoutBtn.setOnClickListener {
            FirebaseAuth.getInstance().signOut()
            val intent = Intent(this, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
        }
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
        val uid = auth.currentUser?.uid ?: return

        repository.savePlace(
            uid = uid,
            place = place,
            onSuccess = {
                Toast.makeText(this, "Lugar guardado", Toast.LENGTH_SHORT).show()
                markerRefresh()
            },
            onError = { e ->
                Toast.makeText(this, "Error al guardar: ${e.message}", Toast.LENGTH_LONG).show()
            }
        )
    }

    private fun loadPlaces() {
        val uid = auth.currentUser?.uid ?: return
        repository.loadPlaces(
            uid,
            onComplete = { places -> places.forEach { addMarkerForPlace(it) } },
            onError = { e -> Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show() }
        )
    }

    // ----------- EDITAR / BORRAR / COMPARTIR -----------
    private fun setupInfoWindowClick() {
        mMap.setOnInfoWindowClickListener { marker ->
            val place = marker.tag as? Place ?: return@setOnInfoWindowClickListener
            val options = arrayOf("Editar", "Borrar", "Compartir")
            AlertDialog.Builder(this)
                .setTitle(place.name)
                .setItems(options) { _, which ->
                    when (which) {
                        0 -> showEditPlaceDialog(place)
                        1 -> deletePlace(place)
                        2 -> sharePlace(place)
                    }
                }
                .show()
        }
    }

    private fun showEditPlaceDialog(place: Place) {
        val editText = EditText(this)
        editText.setText(place.name)
        AlertDialog.Builder(this)
            .setTitle("Editar lugar")
            .setMessage("Modifica el nombre del lugar")
            .setView(editText)
            .setPositiveButton("Guardar") { _, _ ->
                val newName = editText.text.toString().ifBlank { place.name }
                val updatedPlace = place.copy(name = newName)
                val uid = auth.currentUser?.uid ?: return@setPositiveButton
                repository.updatePlace(uid, updatedPlace,
                    onSuccess = {
                        Toast.makeText(this, "Lugar actualizado", Toast.LENGTH_SHORT).show()
                        markerRefresh()
                    },
                    onError = { e -> Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show() }
                )
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun deletePlace(place: Place) {
        AlertDialog.Builder(this)
            .setTitle("Borrar lugar")
            .setMessage("¿Seguro que quieres borrar ${place.name}?")
            .setPositiveButton("Borrar") { _, _ ->
                val uid = auth.currentUser?.uid ?: return@setPositiveButton
                repository.deletePlace(uid, place,
                    onSuccess = {
                        Toast.makeText(this, "Lugar borrado", Toast.LENGTH_SHORT).show()
                        markerRefresh()
                    },
                    onError = { e -> Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show() }
                )
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    // ----------- MARCADORES Y COMPARTIR -----------
    private fun addMarkerForPlace(place: Place) {
        val marker = mMap.addMarker(
            MarkerOptions()
                .position(place.latLng)
                .title(place.name)
        )
        marker?.tag = place
    }

    private fun markerRefresh() {
        mMap.clear()
        loadPlaces()
    }

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

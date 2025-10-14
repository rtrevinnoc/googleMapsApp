package com.google.codelabs.buildyourfirstmap

import android.location.Geocoder
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.RatingBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.PointOfInterest
import com.google.codelabs.buildyourfirstmap.databinding.ActivityMainBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.InputStreamReader
import java.util.Locale

// Data class for saving to Firestore
data class SavedPlace(
    val userId: String = "",
    val name: String = "",
    val vicinity: String = "",
    val comment: String = "",
    val rating: Float = 0f,
    val lat: Double = 0.0,
    val lng: Double = 0.0
)

// A unified data class for any place shown or selected on the map
data class Place(
    val name: String,
    val latLng: LatLng,
    val vicinity: String
)

// --- Data classes for parsing the raw JSON structure ---
data class JsonPlace(val geometry: Geometry, val name: String, val vicinity: String)
data class Geometry(val location: Location)
data class Location(val lat: Double, val lng: Double)

class MainActivity : AppCompatActivity(), OnMapReadyCallback, GoogleMap.OnPoiClickListener {

    private lateinit var mMap: GoogleMap
    private lateinit var binding: ActivityMainBinding
    private var selectedMarker: Marker? = null
    private var customMarker: Marker? = null
    private lateinit var firebaseAuth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    private val places: List<Place> by lazy {
        val jsonStream = resources.openRawResource(R.raw.places)
        val jsonReader = InputStreamReader(jsonStream)
        val jsonPlacesType = object : TypeToken<List<JsonPlace>>() {}.type
        val gson = Gson()
        val jsonPlaces: List<JsonPlace> = gson.fromJson(jsonReader, jsonPlacesType)
        jsonPlaces.map { Place(it.name, LatLng(it.geometry.location.lat, it.geometry.location.lng), it.vicinity) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        firebaseAuth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        binding.fabSavePlace.setOnClickListener { showSavePlaceDialog() }
        binding.fabShowSaved.setOnClickListener { Toast.makeText(this, "Próximamente...", Toast.LENGTH_SHORT).show() }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        mMap.setOnPoiClickListener(this)

        places.forEach { place ->
            mMap.addMarker(MarkerOptions().title(place.name).position(place.latLng))?.tag = place
        }

        if (places.isNotEmpty()) {
            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(places[0].latLng, 14f))
        }

        mMap.setOnMarkerClickListener { marker ->
            customMarker?.remove()
            selectedMarker = marker
            binding.fabSavePlace.visibility = View.VISIBLE
            false
        }

        mMap.setOnMapClickListener { latLng ->
            customMarker?.remove()
            val vicinity = getVicinityFromCoordinates(latLng) ?: ""
            val place = Place("", latLng, vicinity)
            val marker = mMap.addMarker(MarkerOptions().position(latLng).title("Nuevo Lugar"))
            marker?.tag = place
            customMarker = marker
            selectedMarker = marker
            binding.fabSavePlace.visibility = View.VISIBLE
        }
    }

    override fun onPoiClick(poi: PointOfInterest) {
        customMarker?.remove()
        val vicinity = getVicinityFromCoordinates(poi.latLng) ?: poi.name
        val place = Place(poi.name, poi.latLng, vicinity)
        val marker = mMap.addMarker(MarkerOptions().position(poi.latLng).title(poi.name))
        marker?.tag = place
        customMarker = marker
        selectedMarker = marker
        binding.fabSavePlace.visibility = View.VISIBLE
        marker?.showInfoWindow()
    }

    private fun showSavePlaceDialog() {
        val markerToSave = selectedMarker ?: return
        val place = markerToSave.tag as? Place ?: return

        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_save_place, null)
        val nicknameEditText = dialogView.findViewById<EditText>(R.id.edit_text_nickname)
        val vicinityTextView = dialogView.findViewById<TextView>(R.id.text_view_vicinity)
        val commentEditText = dialogView.findViewById<EditText>(R.id.edit_text_comment)
        val ratingBar = dialogView.findViewById<RatingBar>(R.id.rating_bar_review)

        nicknameEditText.setText(place.name)
        vicinityTextView.text = place.vicinity

        AlertDialog.Builder(this)
            .setTitle("Guardar Lugar")
            .setView(dialogView)
            .setPositiveButton("Guardar") { _, _ ->
                val nickname = nicknameEditText.text.toString().ifEmpty { place.name.ifEmpty { "Lugar guardado" } }
                val comment = commentEditText.text.toString()
                val rating = ratingBar.rating
                savePlaceToFirestore(nickname, place.vicinity, comment, rating, place.latLng)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun getVicinityFromCoordinates(latLng: LatLng): String? {
        if (Geocoder.isPresent()) {
            try {
                val geocoder = Geocoder(this, Locale.getDefault())
                val addresses = geocoder.getFromLocation(latLng.latitude, latLng.longitude, 1)
                if (addresses?.isNotEmpty() == true) {
                    return addresses[0].getAddressLine(0)
                }
            } catch (e: Exception) {
                Log.e("Geocoder", "Error getting address", e)
            }
        }
        return null
    }

    private fun savePlaceToFirestore(name: String, vicinity: String, comment: String, rating: Float, latLng: LatLng) {
        val user = firebaseAuth.currentUser ?: return
        val savedPlace = SavedPlace(user.uid, name, vicinity, comment, rating, latLng.latitude, latLng.longitude)

        db.collection("saved_places").add(savedPlace).addOnSuccessListener {
            Toast.makeText(this, "Lugar guardado con éxito.", Toast.LENGTH_SHORT).show()
            binding.fabSavePlace.visibility = View.GONE
            customMarker?.remove()
            selectedMarker = null
        }.addOnFailureListener { e ->
            Toast.makeText(this, "Error al guardar el lugar: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
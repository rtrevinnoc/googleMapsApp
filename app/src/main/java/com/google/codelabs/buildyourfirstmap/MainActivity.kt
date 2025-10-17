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

class MainActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var mMap: GoogleMap

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap


        val monterrey = LatLng(25.6866, -100.3161)
        mMap.addMarker(MarkerOptions().position(monterrey).title("Marcador en Monterrey"))
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(monterrey, 10f))

        // clic en el mapa
        mMap.setOnMapClickListener { latLng ->
            mMap.clear()


            val geocoder = Geocoder(this, Locale.getDefault())
            val direccion = try {
                val resultado = geocoder.getFromLocation(latLng.latitude, latLng.longitude, 1)
                if (resultado != null && resultado.isNotEmpty()) {
                    resultado[0].getAddressLine(0) // primera línea de la dirección
                } else {
                    "Dirección desconocida"
                }
            } catch (e: Exception) {
                "Error al obtener dirección"
            }

            // marcador con la dirección
            val marker = MarkerOptions()
                .position(latLng)
                .title(direccion)
                .snippet("Lat: ${latLng.latitude}, Lng: ${latLng.longitude}")
            mMap.addMarker(marker)


        }
    }
}
package com.google.codelabs.buildyourfirstmap

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions

class MainActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var map: GoogleMap

    // Coordenadas de la FCFM en la UANL
    private val uanlCoordinates = LatLng(25.725575492296038, -100.31519288844862)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initializeMap()
    }


     // Inicializa el fragmento del mapa y solicita el mapa de forma asíncrona.
    private fun initializeMap() {
        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as? SupportMapFragment
        mapFragment?.getMapAsync(this)
    }


     // Se llama cuando el mapa está listo para ser utilizado.

    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap
        setupMap()
    }


     // Configura el marcador y la cámara en el mapa.

    private fun setupMap() {
        // Añade un marcador en la UANL y mueve la cámara
        map.addMarker(
            MarkerOptions()
                .position(uanlCoordinates)
                .title("Marcador en la UANL")
        )

        // Mueve y hace zoom en la cámara hacia la ubicación especificada
        val cameraZoomLevel = 15f
        map.moveCamera(CameraUpdateFactory.newLatLngZoom(uanlCoordinates, cameraZoomLevel))
    }
}
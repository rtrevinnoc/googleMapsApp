package com.google.codelabs.buildyourfirstmap.place

import com.google.android.gms.maps.model.LatLng

// Modelo que representa un lugar guardado en Firestore
data class Place(
    val id: String? = null, // <-- ID del documento en Firestore
    val name: String,
    val address: String,
    val rating: Float,
    val lat: Double,
    val lng: Double
) {
    val latLng: LatLng
        get() = LatLng(lat, lng)
}


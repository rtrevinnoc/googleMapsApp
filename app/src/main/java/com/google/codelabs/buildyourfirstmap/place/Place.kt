package com.google.codelabs.buildyourfirstmap.place

import com.google.android.gms.maps.model.LatLng

// Modelo que representa un lugar guardado en Firestore
data class Place(
    val id: String? = null,
    val name: String = "",
    val address: String = "",
    val rating: Float = 0f,
    val lat: Double = 0.0,
    val lng: Double = 0.0
) {
    val latLng: LatLng
        get() = LatLng(lat, lng)
}

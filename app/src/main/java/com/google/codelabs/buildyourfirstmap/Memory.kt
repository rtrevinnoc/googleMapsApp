package com.google.codelabs.buildyourfirstmap

data class Memory(
    val title: String,
    val description: String,
    val dateMillis: Long,
    val lat: Double,
    val lng: Double,
    val imageUris: List<String> = emptyList()
)


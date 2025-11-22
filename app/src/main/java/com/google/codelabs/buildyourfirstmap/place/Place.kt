// Copyright 2020 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.codelabs.buildyourfirstmap.place

import com.google.android.gms.maps.model.LatLng
import kotlinx.serialization.Serializable

data class Place(
    val id: String = "",
    val name: String = "",
    val latLng: LatLng = LatLng(0.0, 0.0),
    val address: String = "",
    var rating: Float = 0f
) {
    // Constructor secundario para compatibilidad con Firestore
    constructor() : this("", "", LatLng(0.0, 0.0), "", 0f)

    // Convertir a Map para Firestore
    fun toMap(): Map<String, Any> {
        return mapOf(
            "name" to name,
            "latLng" to mapOf(
                "latitude" to latLng.latitude,
                "longitude" to latLng.longitude
            ),
            "address" to address,
            "rating" to rating
        )
    }
}
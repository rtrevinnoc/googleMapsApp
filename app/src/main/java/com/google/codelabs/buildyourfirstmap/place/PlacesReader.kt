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

import android.content.Context
import android.util.Log
import com.google.codelabs.buildyourfirstmap.R
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class PlacesReader(private val context: Context) {

    private val db = FirebaseFirestore.getInstance()
    private val collectionName: String
        get() = context.getString(R.string.placesFirestore)

    /**
     * Lee todos los lugares desde Firestore
     */
    suspend fun read(): List<Place> {
        return try {
            val snapshot = db.collection(collectionName).get().await()
            snapshot.documents.mapNotNull { doc ->
                try {
                    Place(
                        id = doc.id,
                        name = doc.getString("name") ?: "Sin nombre",
                        latLng = LatLng(
                            doc.getDouble("latLng.latitude") ?: 0.0,
                            doc.getDouble("latLng.longitude") ?: 0.0
                        ),
                        address = doc.getString("address") ?: "Sin dirección",
                        rating = doc.getDouble("rating")?.toFloat() ?: 0f
                    )
                } catch (e: Exception) {
                    Log.e("PlacesReader", "Error parsing document ${doc.id}", e)
                    null
                }
            }
        } catch (e: Exception) {
            Log.e("PlacesReader", "Error reading places from Firestore", e)
            emptyList()
        }
    }

    /**
     * Agrega un nuevo lugar a Firestore
     */
    fun addPlace(name: String, latLng: LatLng, address: String, rating: Float) {
        val placeData = hashMapOf(
            "name" to name,
            "latLng" to hashMapOf(
                "latitude" to latLng.latitude,
                "longitude" to latLng.longitude
            ),
            "address" to address,
            "rating" to rating
        )

        db.collection(collectionName)
            .add(placeData)
            .addOnSuccessListener { documentReference ->
                Log.d("PlacesReader", "Place added with ID: ${documentReference.id}")
            }
            .addOnFailureListener { e ->
                Log.e("PlacesReader", "Error adding place", e)
            }
    }

    /**
     * Actualiza un lugar existente en Firestore
     */
    fun updatePlace(place: Place) {
        val placeData = hashMapOf(
            "name" to place.name,
            "latLng" to hashMapOf(
                "latitude" to place.latLng.latitude,
                "longitude" to place.latLng.longitude
            ),
            "address" to place.address,
            "rating" to place.rating
        )

        db.collection(collectionName)
            .document(place.id)
            .set(placeData)
            .addOnSuccessListener {
                Log.d("PlacesReader", "Place updated: ${place.id}")
            }
            .addOnFailureListener { e ->
                Log.e("PlacesReader", "Error updating place", e)
            }
    }

    /**
     * Elimina un lugar de Firestore
     */
    fun deletePlace(placeId: String) {
        db.collection(collectionName)
            .document(placeId)
            .delete()
            .addOnSuccessListener {
                Log.d("PlacesReader", "Place deleted: $placeId")
            }
            .addOnFailureListener { e ->
                Log.e("PlacesReader", "Error deleting place", e)
            }
    }
}
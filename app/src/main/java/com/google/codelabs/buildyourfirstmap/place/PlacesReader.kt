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

    //Leer lugares de firestore
    suspend fun read(): List<Place> {
        return try {
            val snapshot = db.collection(collectionName).get().await()
            Log.d("PlacesReader", "Found ${snapshot.documents.size} documents in collection")
            
            snapshot.documents.mapNotNull { doc ->
                try {
                    val latitude: Double
                    val longitude: Double

                    val lat = doc.get("lat")
                    val lng = doc.get("lng")
                    
                    if (lat != null && lng != null) {

                        latitude = when (lat) {
                            is Double -> lat
                            is Number -> lat.toDouble()
                            else -> {
                                Log.w("PlacesReader", "Invalid lat type for document ${doc.id}: ${lat::class.simpleName}")
                                0.0
                            }
                        }
                        longitude = when (lng) {
                            is Double -> lng
                            is Number -> lng.toDouble()
                            else -> {
                                Log.w("PlacesReader", "Invalid lng type for document ${doc.id}: ${lng::class.simpleName}")
                                0.0
                            }
                        }
                    } else {
                        val latLngMap = doc.get("latLng") as? Map<String, Any>
                        val latValue = latLngMap?.get("latitude")
                        val lngValue = latLngMap?.get("longitude")
                        
                        latitude = when (latValue) {
                            is Double -> latValue
                            is Number -> latValue.toDouble()
                            else -> {
                                Log.w("PlacesReader", "No valid latitude found for document ${doc.id}")
                                0.0
                            }
                        }
                        longitude = when (lngValue) {
                            is Double -> lngValue
                            is Number -> lngValue.toDouble()
                            else -> {
                                Log.w("PlacesReader", "No valid longitude found for document ${doc.id}")
                                0.0
                            }
                        }
                    }

                    val latLng = if (latitude in -90.0..90.0 && longitude in -180.0..180.0 && latitude != 0.0 && longitude != 0.0) {
                        LatLng(latitude, longitude)
                    } else {
                        Log.w("PlacesReader", "Invalid coordinates for document ${doc.id}: ($latitude, $longitude)")
                        null
                    }

                    if (latLng == null) {
                        return@mapNotNull null
                    }

                    val name = doc.getString("name") ?: "Sin nombre"

                    val address = doc.getString("vicinity") 
                        ?: doc.getString("address") 
                        ?: "Sin dirección"
                    

                    val rating = doc.getDouble("rating")?.toFloat() ?: 0f
                    
                    Log.d("PlacesReader", "Loaded place: $name at ($latitude, $longitude)")
                    
                    Place(
                        id = doc.id,
                        name = name,
                        latLng = latLng,
                        address = address,
                        rating = rating
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

    //Agregar nuevo lugar
    suspend fun addPlace(name: String, latLng: LatLng, address: String, rating: Float): String? {
        return try {
            if (name.isBlank()) {
                Log.w("PlacesReader", "Cannot add place with blank name")
                return null
            }
            val placeData = hashMapOf(
                "name" to name.trim(),
                "lat" to latLng.latitude,
                "lng" to latLng.longitude,
                "vicinity" to address.trim().ifBlank { "Sin dirección" },
                "rating" to rating.coerceIn(0f, 5f)
            )

            val documentReference = db.collection(collectionName)
                .add(placeData)
                .await()
            
            Log.d("PlacesReader", "Place added with ID: ${documentReference.id}")
            documentReference.id
        } catch (e: Exception) {
            Log.e("PlacesReader", "Error adding place", e)
            null
        }
    }


    fun updatePlace(place: Place) {

        val placeData = hashMapOf(
            "name" to place.name,
            "lat" to place.latLng.latitude,
            "lng" to place.latLng.longitude,
            "vicinity" to place.address,
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

    suspend fun deletePlace(placeId: String): Boolean {
        return try {
            db.collection(collectionName)
                .document(placeId)
                .delete()
                .await()
            Log.d("PlacesReader", "Place deleted: $placeId")
            true
        } catch (e: Exception) {
            Log.e("PlacesReader", "Error deleting place", e)
            false
        }
    }
}
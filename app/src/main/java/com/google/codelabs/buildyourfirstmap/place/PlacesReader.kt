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

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import android.content.Context
import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore

class PlacesReader(private val context: Context) {

    private val db = FirebaseFirestore.getInstance()

    suspend fun read(): List<Place> {
        val places = mutableListOf<Place>()
        val snapshot = db.collection("places").get().await()

        for (doc in snapshot.documents) {
            val place = Place(
                id = doc.id, //ID del documento
                name = doc.getString("name") ?: "",
                address = doc.getString("address") ?: "",
                lat = doc.getDouble("lat") ?: 0.0,
                lng = doc.getDouble("lng") ?: 0.0,
                rating = (doc.getDouble("rating") ?: 0.0).toFloat()
            )
            places.add(place)
        }

        return places
    }
}
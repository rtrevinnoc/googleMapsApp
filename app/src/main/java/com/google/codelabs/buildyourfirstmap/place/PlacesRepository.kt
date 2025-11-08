package com.google.codelabs.buildyourfirstmap.place

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class PlacesRepository(private val db: FirebaseFirestore = FirebaseFirestore.getInstance()) {

    suspend fun getPlaces(uid: String? = null): List<Place> {
        val query = db.collection("places")
        val snapshot = if (uid != null) query.whereEqualTo("ownerUid", uid).get().await()
        else query.get().await()
        return snapshot.documents.mapNotNull { doc ->
            doc.toObject(Place::class.java)?.copy(id = doc.id)
        }
    }

    suspend fun savePlace(place: Place, uid: String?) {
        val data = hashMapOf(
            "name" to place.name,
            "lat" to place.lat,
            "lng" to place.lng,
            "address" to place.address,
            "rating" to place.rating,
            "ownerUid" to uid
        )
        db.collection("places").add(data).await()
    }
}

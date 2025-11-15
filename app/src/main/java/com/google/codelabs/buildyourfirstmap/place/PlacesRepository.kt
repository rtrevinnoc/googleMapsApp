package com.google.codelabs.buildyourfirstmap.place

import com.google.firebase.firestore.FirebaseFirestore

class PlacesRepository(private val db: FirebaseFirestore) {

    fun savePlace(
        uid: String,
        place: Place,
        onSuccess: (Place) -> Unit,
        onError: (Exception) -> Unit
    ) {
        val data = hashMapOf(
            "name" to place.name,
            "lat" to place.lat,
            "lng" to place.lng,
            "address" to place.address,
            "rating" to place.rating
        )

        db.collection("users").document(uid)
            .collection("places")
            .add(data)
            .addOnSuccessListener { ref ->
                val placeWithId = place.copy(id = ref.id)
                onSuccess(placeWithId)
            }
            .addOnFailureListener { e -> onError(e) }
    }

    fun loadPlaces(
        uid: String,
        onComplete: (List<Place>) -> Unit,
        onError: (Exception) -> Unit
    ) {
        db.collection("users").document(uid)
            .collection("places")
            .get()
            .addOnSuccessListener { snap ->
                val list = snap.documents.mapNotNull { d ->
                    val name = d.getString("name") ?: return@mapNotNull null
                    val lat = d.getDouble("lat") ?: return@mapNotNull null
                    val lng = d.getDouble("lng") ?: return@mapNotNull null
                    val address = d.getString("address") ?: ""
                    val rating = (d.getDouble("rating") ?: 0.0).toFloat()
                    Place(
                        id = d.id,
                        name = name,
                        lat = lat,
                        lng = lng,
                        address = address,
                        rating = rating
                    )
                }
                onComplete(list)
            }
            .addOnFailureListener { e -> onError(e) }
    }

    // ---------- NUEVAS FUNCIONES ----------

    fun updatePlace(uid: String, place: Place, onSuccess: () -> Unit, onError: (Exception) -> Unit) {
        if (place.id == null) return
        db.collection("users").document(uid)
            .collection("places").document(place.id)
            .set(
                hashMapOf(
                    "name" to place.name,
                    "lat" to place.lat,
                    "lng" to place.lng,
                    "address" to place.address,
                    "rating" to place.rating
                )
            )
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { e -> onError(e) }
    }

    fun deletePlace(uid: String, place: Place, onSuccess: () -> Unit, onError: (Exception) -> Unit) {
        if (place.id == null) return
        db.collection("users").document(uid)
            .collection("places").document(place.id)
            .delete()
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { e -> onError(e) }
    }
}

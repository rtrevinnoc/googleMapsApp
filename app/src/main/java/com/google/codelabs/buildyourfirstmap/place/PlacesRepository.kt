package com.google.codelabs.buildyourfirstmap.place

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore

class PlacesRepository(private val db: FirebaseFirestore) {

    // Guarda un place en: users/{uid}/places/{autoId}
    fun savePlace(
        uid: String,
        place: Place,
        onSuccess: (String) -> Unit,
        onError: (Exception) -> Unit
    ) {
        val data = hashMapOf(
            "name" to place.name,
            "lat" to place.lat,
            "lng" to place.lng,
            "address" to place.address,
            "rating" to place.rating,
            "visited" to place.visited,
            "ownerUid" to uid
        )

        db.collection("users").document(uid)
            .collection("places")
            .add(data)
            .addOnSuccessListener { ref -> onSuccess(ref.id) }
            .addOnFailureListener { e -> onError(e) }
    }

    // Carga todos los places de un usuario
    fun loadPlaces(
        uid: String,
        onComplete: (List<Place>) -> Unit,
        onError: (Exception) -> Unit
    ) {
        db.collection("users").document(uid)
            .collection("places")
            .get()
            .addOnSuccessListener { snap ->
                val list = snap.documents.mapNotNull { d: DocumentSnapshot ->
                    val name = d.getString("name") ?: return@mapNotNull null
                    val lat = d.getDouble("lat") ?: return@mapNotNull null
                    val lng = d.getDouble("lng") ?: return@mapNotNull null
                    val address = d.getString("address") ?: ""
                    val rating = (d.getDouble("rating") ?: 0.0).toFloat()
                    val visited = d.getBoolean("visited") ?: false
                    Place(
                        id = d.id,
                        name = name,
                        lat = lat,
                        lng = lng,
                        address = address,
                        rating = rating,
                        visited = visited
                    )
                }
                onComplete(list)
            }
            .addOnFailureListener { e -> onError(e) }
    }

    fun updatePlace(
        uid: String,
        place: Place,
        onSuccess: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        val id = place.id ?: return
        val data = mapOf(
            "name" to place.name,
            "lat" to place.lat,
            "lng" to place.lng,
            "address" to place.address,
            "rating" to place.rating,
            "visited" to place.visited
        )

        db.collection("users").document(uid)
            .collection("places").document(id)
            .set(data)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { e -> onError(e) }
    }

    fun deletePlace(
        uid: String,
        place: Place,
        onSuccess: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        val id = place.id ?: return
        db.collection("users").document(uid)
            .collection("places").document(id)
            .delete()
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { e -> onError(e) }
    }

    // ---------------- NOTES (subcollection) ----------------

    // Agrega una nota a users/{uid}/places/{placeId}/notes
    fun addNote(
        uid: String,
        placeId: String,
        text: String,
        onSuccess: (String) -> Unit,
        onError: (Exception) -> Unit
    ) {
        val noteData = hashMapOf(
            "text" to text,
            "createdAt" to Timestamp.now(),
            "authorUid" to uid
        )
        db.collection("users").document(uid)
            .collection("places").document(placeId)
            .collection("notes")
            .add(noteData)
            .addOnSuccessListener { ref -> onSuccess(ref.id) }
            .addOnFailureListener { e -> onError(e) }
    }

    // Obtiene notas de una place (ordenadas por fecha ascendente)
    fun getNotes(
        uid: String,
        placeId: String,
        onComplete: (List<Note>) -> Unit,
        onError: (Exception) -> Unit
    ) {
        db.collection("users").document(uid)
            .collection("places").document(placeId)
            .collection("notes")
            .orderBy("createdAt")
            .get()
            .addOnSuccessListener { snap ->
                val list = snap.documents.mapNotNull { d ->
                    val text = d.getString("text") ?: return@mapNotNull null
                    val createdAt = d.getTimestamp("createdAt")
                    val author = d.getString("authorUid")
                    Note(
                        id = d.id,
                        text = text,
                        createdAt = createdAt,
                        authorUid = author
                    )
                }
                onComplete(list)
            }
            .addOnFailureListener { e -> onError(e) }
    }

    fun updateNote(
        uid: String,
        placeId: String,
        note: Note,
        onSuccess: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        val noteId = note.id ?: return
        val data = mapOf(
            "text" to note.text,
            "createdAt" to note.createdAt,
            "authorUid" to note.authorUid
        )
        db.collection("users").document(uid)
            .collection("places").document(placeId)
            .collection("notes").document(noteId)
            .set(data)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { e -> onError(e) }
    }

    fun deleteNote(
        uid: String,
        placeId: String,
        note: Note,
        onSuccess: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        val noteId = note.id ?: return
        db.collection("users").document(uid)
            .collection("places").document(placeId)
            .collection("notes").document(noteId)
            .delete()
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { e -> onError(e) }
    }
}

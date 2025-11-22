package com.google.codelabs.buildyourfirstmap

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.codelabs.buildyourfirstmap.place.Place
import com.google.codelabs.buildyourfirstmap.place.PlacesRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class SavedPlacesActivity : AppCompatActivity() {

    private lateinit var recycler: RecyclerView
    private val repository = PlacesRepository(FirebaseFirestore.getInstance())
    private val uid = FirebaseAuth.getInstance().currentUser!!.uid
    private var places = mutableListOf<Place>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_saved_places)

        recycler = findViewById(R.id.recyclerPlaces)
        recycler.layoutManager = LinearLayoutManager(this)

        loadPlaces()
    }

    private fun loadPlaces() {
        repository.loadPlaces(
            uid,
            onComplete = { loaded ->
                places = loaded.toMutableList()
                recycler.adapter = PlacesAdapter(
                    places,
                    onVisitedChanged = { place, visited ->
                        updateVisited(place, visited)
                    },
                    onNotesClick = { place ->
                        openNotes(place)
                    }
                )
            },
            onError = {
                Toast.makeText(this, "Error al cargar lugares", Toast.LENGTH_LONG).show()
            }
        )
    }

    private fun updateVisited(place: Place, visited: Boolean) {
        val updated = place.copy(visited = visited)
        repository.updatePlace(
            uid,
            updated,
            onSuccess = {},
            onError = {}
        )
    }

    private fun openNotes(place: Place) {
        val intent = Intent(this, NotesActivity::class.java).apply {
            putExtra("placeId", place.id)
            putExtra("placeName", place.name)
        }
        startActivity(intent)
    }
}

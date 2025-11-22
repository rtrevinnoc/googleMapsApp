package com.google.codelabs.buildyourfirstmap

import android.os.Bundle
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
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
                        showNotesDialog(place)
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

    private fun showNotesDialog(place: Place) {
        val edit = EditText(this)
        edit.setText(place.notes ?: "")
        edit.setLines(6)
        edit.setPadding(20, 20, 20, 20)

        AlertDialog.Builder(this)
            .setTitle("Notas de ${place.name}")
            .setView(edit)
            .setPositiveButton("Guardar") { _, _ ->
                val updated = place.copy(notes = edit.text.toString())
                repository.updatePlace(
                    uid,
                    updated,
                    onSuccess = {},
                    onError = {}
                )
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
}

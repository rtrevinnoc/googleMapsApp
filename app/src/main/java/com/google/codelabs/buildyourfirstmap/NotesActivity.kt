package com.google.codelabs.buildyourfirstmap

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.codelabs.buildyourfirstmap.place.Note
import com.google.codelabs.buildyourfirstmap.place.PlacesRepository

class NotesActivity : AppCompatActivity() {

    private lateinit var repo: PlacesRepository
    private lateinit var recycler: RecyclerView
    private lateinit var adapter: NotesAdapter
    private lateinit var edtNewNote: EditText
    private var notes = mutableListOf<Note>()

    private lateinit var uid: String
    private lateinit var placeId: String
    private lateinit var placeName: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_notes)

        uid = FirebaseAuth.getInstance().currentUser?.uid ?: run {
            finish(); return
        }

        placeId = intent.getStringExtra("placeId") ?: run { finish(); return }
        placeName = intent.getStringExtra("placeName") ?: ""

        repo = PlacesRepository(FirebaseFirestore.getInstance())

        findViewById<TextView>(R.id.txtPlaceTitle).text = placeName
        recycler = findViewById(R.id.recyclerNotes)
        recycler.layoutManager = LinearLayoutManager(this)
        adapter = NotesAdapter(notes, onEdit = { note -> editNoteDialog(note) }, onDelete = { note -> deleteNote(note) })
        recycler.adapter = adapter

        edtNewNote = findViewById(R.id.edtNewNote)
        findViewById<Button>(R.id.btnAddNote).setOnClickListener {
            val text = edtNewNote.text.toString().ifBlank { return@setOnClickListener }
            repo.addNote(uid, placeId, text, onSuccess = {
                edtNewNote.setText("")
                loadNotes()
            }, onError = { e -> Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show() })
        }

        loadNotes()
    }

    private fun loadNotes() {
        repo.getNotes(uid, placeId, onComplete = { loaded ->
            notes.clear()
            notes.addAll(loaded)
            adapter.notifyDataSetChanged()
        }, onError = { e ->
            Toast.makeText(this, "Error cargando notas: ${e.message}", Toast.LENGTH_LONG).show()
        })
    }

    private fun editNoteDialog(note: Note) {
        val edt = EditText(this)
        edt.setText(note.text)
        AlertDialog.Builder(this)
            .setTitle("Editar nota")
            .setView(edt)
            .setPositiveButton("Guardar") { _, _ ->
                val updated = note.copy(text = edt.text.toString())
                repo.updateNote(uid, placeId, updated,
                    onSuccess = { loadNotes() },
                    onError = { e -> Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show() }
                )
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun deleteNote(note: Note) {
        AlertDialog.Builder(this)
            .setTitle("Borrar nota")
            .setMessage("¿Borrar nota?")
            .setPositiveButton("Borrar") { _, _ ->
                repo.deleteNote(uid, placeId, note,
                    onSuccess = { loadNotes() },
                    onError = { e -> Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show() }
                )
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
}

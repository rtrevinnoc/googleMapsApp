package com.google.codelabs.buildyourfirstmap

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.codelabs.buildyourfirstmap.R
import com.google.codelabs.buildyourfirstmap.place.Note
import java.text.SimpleDateFormat
import java.util.*



import java.util.*



class NotesAdapter(
    private val notes: List<Note>,
    private val onEdit: (Note) -> Unit,
    private val onDelete: (Note) -> Unit
) : RecyclerView.Adapter<NotesAdapter.ViewHolder>() {

    class ViewHolder(v: View) : RecyclerView.ViewHolder(v) {
        val txtText: TextView = v.findViewById(R.id.txtNoteText)
        val txtDate: TextView = v.findViewById(R.id.txtNoteDate)
        val btnEdit: Button = v.findViewById(R.id.btnEditNote)
        val btnDelete: Button = v.findViewById(R.id.btnDeleteNote)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_note, parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount(): Int = notes.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val note = notes[position]
        holder.txtText.text = note.text

        val ts = note.createdAt
        val dateStr = if (ts != null) {
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            sdf.format(ts.toDate())
        } else {
            ""
        }
        holder.txtDate.text = dateStr

        holder.btnEdit.setOnClickListener { onEdit(note) }
        holder.btnDelete.setOnClickListener { onDelete(note) }
    }
}

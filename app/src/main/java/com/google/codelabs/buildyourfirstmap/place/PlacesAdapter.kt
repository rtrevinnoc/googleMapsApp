package com.google.codelabs.buildyourfirstmap

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.codelabs.buildyourfirstmap.place.Place

class PlacesAdapter(
    private val places: List<Place>,
    private val onVisitedChanged: (Place, Boolean) -> Unit,
    private val onNotesClick: (Place) -> Unit
) : RecyclerView.Adapter<PlacesAdapter.ViewHolder>() {

    class ViewHolder(v: View) : RecyclerView.ViewHolder(v) {
        val name: TextView = v.findViewById(R.id.txtName)
        val address: TextView = v.findViewById(R.id.txtAddress)
        val visited: CheckBox = v.findViewById(R.id.checkVisited)
        val btnNotes: Button = v.findViewById(R.id.btnNotes)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_place, parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount() = places.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val place = places[position]

        holder.name.text = place.name
        holder.address.text = place.address
        holder.visited.isChecked = place.visited

        holder.visited.setOnCheckedChangeListener { _, checked ->
            onVisitedChanged(place, checked)
        }

        holder.btnNotes.setOnClickListener {
            onNotesClick(place)
        }
    }
}

package com.google.codelabs.buildyourfirstmap

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.Marker
import com.google.codelabs.buildyourfirstmap.place.Place
import java.text.SimpleDateFormat
import java.util.*

class MarkerPopupAdapter(
    private val context: Context
) : GoogleMap.InfoWindowAdapter {
    override fun getInfoContents(marker: Marker): View? {
        // Intentar Place primero, luego Memory
        val tag = marker.tag
        val view = LayoutInflater.from(context).inflate(
            R.layout.marker_popup, null
        )

        when (tag) {
            is Place -> {
                view.findViewById<TextView>(R.id.marker_popup_title).text = tag.name
                view.findViewById<TextView>(R.id.marker_popup_address).text = tag.address
                view.findViewById<TextView>(R.id.marker_popup_rating).text = "Puntuación: %.2f".format(tag.rating)
            }
            is Memory -> {
                val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                view.findViewById<TextView>(R.id.marker_popup_title).text = tag.title
                view.findViewById<TextView>(R.id.marker_popup_address).text = tag.description
                view.findViewById<TextView>(R.id.marker_popup_rating).text =
                    "Fecha: ${sdf.format(Date(tag.dateMillis))} · Fotos: ${tag.imageUris.size}"
            }
            else -> return null
        }

        return view
    }

    override fun getInfoWindow(marker: Marker): View? {
        return null
    }
}
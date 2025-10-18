package com.google.codelabs.buildyourfirstmap

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.Marker
import com.google.codelabs.buildyourfirstmap.place.Place

class MarkerPopupAdapter(
    private val context: Context
) : GoogleMap.InfoWindowAdapter { // por defecto cuando se hace un marcador en el mapa lanza un popup personalizado
    override fun getInfoContents(marker: Marker): View? { // es el contenido del infoWindowAdapter
        val place = marker.tag as? Place ?: return null //

        val view = LayoutInflater.from(context).inflate(
            R.layout.marker_popup, null
        )
        view.findViewById<TextView>(R.id.marker_popup_title).text = place.name
        view.findViewById<TextView>(R.id.marker_popup_address).text = place.address
        view.findViewById<TextView>(R.id.marker_popup_rating).text = "Puntuación: %.2f".format(place.rating) //place.rating es float

        return view
    }

    override fun getInfoWindow(marker: Marker): View? {
        return null
    }
}
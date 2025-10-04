package com.fcfm.agosto.aplicacionesmoviles

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.TextView
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.Marker
import com.fcfm.agosto.aplicacionesmoviles.place.Place
import com.fcfm.agosto.aplicacionesmoviles.R
import com.fcfm.agosto.aplicacionesmoviles.scores.Score
import com.fcfm.agosto.aplicacionesmoviles.scores.ScoresReader

class MarkerPopupAdapter(
    private val context: Context
) : GoogleMap.InfoWindowAdapter {
    private val scoresReader = ScoresReader(context)

    override fun getInfoContents(marker: Marker): View? {
        val place = marker.tag as? Place ?: return null

        val view = LayoutInflater.from(context).inflate(
            R.layout.marker_popup, null
        )
        view.findViewById<TextView>(R.id.marker_popup_title).text = place.name
        view.findViewById<TextView>(R.id.marker_popup_address).text = place.address
        view.findViewById<TextView>(R.id.marker_popup_rating).text = "Puntuación: %.2f".format(place.rating)
        view.findViewById<Button>(R.id.addScore).setOnClickListener {
            addScore(place)
        }

        return view
    }

    override fun getInfoWindow(marker: Marker): View? {
        return null
    }

    fun addScore(place: Place) {
        val score = Score("usuario", place.rating, "")
        scoresReader.doPost(score)
    }
}
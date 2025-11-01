package com.google.codelabs.buildyourfirstmap

import android.app.Activity
import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.Marker
import com.google.codelabs.buildyourfirstmap.place.Place
import com.google.codelabs.buildyourfirstmap.scores.ScoresReader
import com.google.firebase.auth.FirebaseAuth

class MarkerPopupAdapter(private val context: Context) : GoogleMap.InfoWindowAdapter {

    private val inflater = LayoutInflater.from(context)
    private val scoresReader = ScoresReader(context)
    private val auth = FirebaseAuth.getInstance()

    override fun getInfoContents(marker: Marker): View {
        val place = marker.tag as? Place ?: return View(context)
        val view = inflater.inflate(R.layout.marker_popup, null)

        val titleView = view.findViewById<TextView>(R.id.marker_popup_title)
        val addressView = view.findViewById<TextView>(R.id.marker_popup_address)
        val ratingView = view.findViewById<TextView>(R.id.marker_popup_rating)

        titleView.text = place.name
        addressView.text = place.address ?: "Dirección no disponible"
        ratingView.text = "Cargando puntuación..."

        // Obtener promedio actual desde Google Sheets
        Thread {
            try {
                val average = scoresReader.get(place.id ?: place.name)
                (context as? Activity)?.runOnUiThread {
                    ratingView.text = String.format("Puntuación promedio: %.2f ★", average)
                }
            }

            catch (e: Exception) {
                Log.e("MarkerPopup", "Error al cargar puntuación", e)
                (context as? Activity)?.runOnUiThread {
                    ratingView.text = "Puntuación no disponible"
                }
            }
        }.start()

        return view
    }

    override fun getInfoWindow(marker: Marker): View? = null
}
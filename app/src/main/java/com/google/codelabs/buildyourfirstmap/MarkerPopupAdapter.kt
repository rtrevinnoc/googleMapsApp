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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class MarkerPopupAdapter(
    private val context: Context,
    private val lifecycleScope: CoroutineScope
) : GoogleMap.InfoWindowAdapter {

    private val inflater = LayoutInflater.from(context)
    private val scoresReader = ScoresReader(context)

    override fun getInfoContents(marker: Marker): View {
        val place = marker.tag as? Place ?: return View(context)
        val view = inflater.inflate(R.layout.marker_popup, null)

        val titleView = view.findViewById<TextView>(R.id.marker_popup_title)
        val addressView = view.findViewById<TextView>(R.id.marker_popup_address)
        val ratingView = view.findViewById<TextView>(R.id.marker_popup_rating)

        titleView.text = place.name
        addressView.text = place.address.ifBlank { "Dirección no disponible" }

        // Si ya se obtuvo el promedio, mostrarlo sin volver a consultar
        if (place.rating > 0f) {
            ratingView.text = "Puntuación promedio: %.2f ★".format(place.rating)
            return view
        }

        // Si aún no tiene promedio, mostrar mensaje y consultar una sola vez
        ratingView.text = "Cargando puntuación..."

        lifecycleScope.launch {
            try {
                // 🔹 Obtener promedio solo una vez
                val average = scoresReader.getAverageScoreFor(place.name)
                place.rating = average  // 👈 guardar para no volver a leer

                // Determinar mensaje
                val message = if (average <= 0f) {
                    "Sin puntuaciones aún"
                } else {
                    "Puntuación promedio: %.2f ★".format(average)
                }

                // Actualizar texto y refrescar el popup solo una vez
                ratingView.text = message

                // ✅ Importante: NO hacer hide/show otra vez si ya lo mostró
                if (marker.isInfoWindowShown) {
                    marker.showInfoWindow()
                }

            } catch (e: Exception) {
                Log.e("MarkerPopup", "Error al cargar puntuación", e)
                ratingView.text = "Error al cargar puntuación"
            }
        }

        return view
    }

    override fun getInfoWindow(marker: Marker): View? = null
}


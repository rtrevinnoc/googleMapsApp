package com.google.codelabs.buildyourfirstmap

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.content.Intent
import android.app.Activity
import android.widget.Button
import android.app.AlertDialog
import android.view.LayoutInflater
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.net.Uri
import java.text.SimpleDateFormat
import java.util.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.codelabs.buildyourfirstmap.place.Place
import com.google.codelabs.buildyourfirstmap.Memory

class MainActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var mMap: GoogleMap
    private val REQUEST_ADD_MEMORY = 1001
    private var isPickingLocation = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        isPickingLocation = intent.getBooleanExtra("pick_location", false)

        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        // Botón para agregar memoria desde la posición actual de la cámara (o por defecto)
        val addBtn = findViewById<Button>(R.id.button_add_memory)
        addBtn.setOnClickListener {
            val defaultPos = LatLng(25.6866, -100.3161)
            val target = if (::mMap.isInitialized) mMap.cameraPosition.target else defaultPos
            val intent = Intent(this, AddMemoryActivity::class.java).apply {
                putExtra("lat", target.latitude)
                putExtra("lng", target.longitude)
            }
            startActivityForResult(intent, REQUEST_ADD_MEMORY)
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap

        // Usar el adapter personalizado para mostrar detalles desde Place o Memory
        mMap.setInfoWindowAdapter(MarkerPopupAdapter(this))

        val monterrey = LatLng(25.6866, -100.3161)

        mMap.addMarker(MarkerOptions().position(monterrey).title("Marcador en Monterrey"))
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(monterrey, 10f))

        // Si estamos en modo "pick location", devolver la coordenada al hacer click en el mapa
        if (isPickingLocation) {
            mMap.setOnMapClickListener { latLng ->
                val data = Intent().apply {
                    putExtra("picked_lat", latLng.latitude)
                    putExtra("picked_lng", latLng.longitude)
                }
                setResult(Activity.RESULT_OK, data)
                finish()
            }
            return
        }

        // Al hacer long-press, abrir la pantalla para crear un recuerdo en esa ubicación
        mMap.setOnMapLongClickListener { latLng ->
            val intent = Intent(this, AddMemoryActivity::class.java).apply {
                putExtra("lat", latLng.latitude)
                putExtra("lng", latLng.longitude)
            }
            startActivityForResult(intent, REQUEST_ADD_MEMORY)
        }

        // Al hacer click en un marcador, si tiene tag Memory, mostrar detalle
        mMap.setOnMarkerClickListener { marker ->
            val tag = marker.tag
            if (tag is Memory) {
                showMemoryDialog(tag)
                true
            } else {
                false
            }
        }
    }

    private fun showMemoryDialog(memory: Memory) {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_memory_detail, null)
        val titleView = view.findViewById<TextView>(R.id.detail_title)
        val descView = view.findViewById<TextView>(R.id.detail_description)
        val dateView = view.findViewById<TextView>(R.id.detail_date)
        val locView = view.findViewById<TextView>(R.id.detail_location)
        val imagesContainer = view.findViewById<LinearLayout>(R.id.detail_images_container)

        titleView.text = memory.title
        descView.text = memory.description
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        dateView.text = sdf.format(Date(memory.dateMillis))
        locView.text = "Lat: %.5f, Lng: %.5f".format(memory.lat, memory.lng)

        imagesContainer.removeAllViews()
        for (uriStr in memory.imageUris) {
            try {
                val iv = ImageView(this)
                val lp = LinearLayout.LayoutParams(300, 300)
                lp.setMargins(8, 8, 8, 8)
                iv.layoutParams = lp
                iv.scaleType = ImageView.ScaleType.CENTER_CROP
                iv.setImageURI(Uri.parse(uriStr))
                imagesContainer.addView(iv)
            } catch (e: Exception) {
                // ignore individual image errors
            }
        }

        AlertDialog.Builder(this)
            .setView(view)
            .setPositiveButton("Cerrar", null)
            .show()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_ADD_MEMORY && resultCode == Activity.RESULT_OK && data != null) {
            val title = data.getStringExtra("title") ?: "Recuerdo"
            val description = data.getStringExtra("description") ?: ""
            val dateMillis = data.getLongExtra("dateMillis", System.currentTimeMillis())
            val lat = data.getDoubleExtra("lat", 0.0)
            val lng = data.getDoubleExtra("lng", 0.0)
            val images = data.getStringArrayListExtra("images") ?: arrayListOf()

            val memory = Memory(
                title = title,
                description = description,
                dateMillis = dateMillis,
                lat = lat,
                lng = lng,
                imageUris = images
            )

            val latLng = LatLng(lat, lng)
            val marker = mMap.addMarker(MarkerOptions().position(latLng).title(memory.title))
            marker?.tag = memory
            marker?.showInfoWindow()
        }
    }
}
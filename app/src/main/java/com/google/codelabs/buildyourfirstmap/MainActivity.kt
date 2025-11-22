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
import android.view.View
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.BitmapDescriptorFactory

class MainActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var mMap: GoogleMap
    private val REQUEST_ADD_MEMORY = 1001
    private var isPickingLocation = false

    // marcador temporal para selección de ubicación
    private var pickMarker: Marker? = null

    // Coordenadas iniciales para el modo selección (vienen del formulario o default Monterrey)
    private var pickInitialLat: Double = Double.NaN
    private var pickInitialLng: Double = Double.NaN

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        isPickingLocation = intent.getBooleanExtra("pick_location", false)

        // Leer coordenadas iniciales del formulario (si existen)
        pickInitialLat = intent.getDoubleExtra("initial_lat", Double.NaN)
        pickInitialLng = intent.getDoubleExtra("initial_lng", Double.NaN)

        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        // Botón para agregar memoria desde la posición actual de la cámara (o por defecto)
        val addBtn = findViewById<Button>(R.id.button_add_memory)
        val confirmBtn = findViewById<Button>(R.id.button_confirm_location)
        val cancelBtn = findViewById<Button>(R.id.button_cancel_selection)
        // inicialmente oculto; se mostrará si estamos en modo selección
        confirmBtn.visibility = View.GONE
        cancelBtn.visibility = View.GONE

        addBtn.setOnClickListener {
            val defaultPos = LatLng(25.6866, -100.3161)
            val target = if (::mMap.isInitialized) mMap.cameraPosition.target else defaultPos
            val intent = Intent(this, AddMemoryActivity::class.java).apply {
                putExtra("lat", target.latitude)
                putExtra("lng", target.longitude)
            }
            startActivityForResult(intent, REQUEST_ADD_MEMORY)
        }

        // Si estamos en modo selección y el usuario pulsa confirmar, devolvemos la posición del pickMarker
        confirmBtn.setOnClickListener {
            pickMarker?.let { mk ->
                val pos = mk.position
                val data = Intent().apply {
                    putExtra("picked_lat", pos.latitude)
                    putExtra("picked_lng", pos.longitude)
                }
                setResult(Activity.RESULT_OK, data)
                finish()
            }
        }

        // Si el usuario pulsa cancelar, simplemente cerramos la actividad sin resultado
        cancelBtn.setOnClickListener {
            setResult(Activity.RESULT_CANCELED)
            finish()
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap

        // Usar el adapter personalizado para mostrar detalles desde Place o Memory
        mMap.setInfoWindowAdapter(MarkerPopupAdapter(this))

        val monterrey = LatLng(25.6866, -100.3161)

        //mMap.addMarker(MarkerOptions().position(monterrey).title("Marcador en Monterrey"))
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(monterrey, 10f))

        // Si estamos en modo "pick location", configuramos marcador draggable de color distinto
        if (isPickingLocation) {
            // ocultar botón agregar
            val addBtn = findViewById<Button>(R.id.button_add_memory)
            addBtn.visibility = View.GONE

            // mostrar botón confirmar
            val confirmBtn = findViewById<Button>(R.id.button_confirm_location)
            confirmBtn.visibility = View.VISIBLE

            // mostrar boton cancelar
            val cancelBtn = findViewById<Button>(R.id.button_cancel_selection)
            cancelBtn.visibility = View.VISIBLE

            // posición inicial: usar las coordenadas del formulario si existen, sino Monterrey
            val initPos = if (!pickInitialLat.isNaN() && !pickInitialLng.isNaN()) {
                LatLng(pickInitialLat, pickInitialLng)
            } else {
                monterrey
            }

            // crear marcador draggable con color distinto
            pickMarker = mMap.addMarker(
                MarkerOptions()
                    .position(initPos)
                    .title("Selecciona una ubicación")
                    .draggable(true)
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE))
            )

            // permitir que el usuario arrastre; también actualizamos marker si se mueve la cámara (opcional)
            mMap.setOnMarkerDragListener(object : GoogleMap.OnMarkerDragListener {
                override fun onMarkerDragStart(marker: Marker) { /* no-op */ }
                override fun onMarkerDrag(marker: Marker) { /* no-op */ }
                override fun onMarkerDragEnd(marker: Marker) { /* no-op */ }
            })

            // Si el usuario hace click en el mapa también podemos mover el marcador allí
            mMap.setOnMapClickListener { latLng ->
                pickMarker?.position = latLng
            }

            // centramos la cámara sobre el marcador para que sea visible
            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(initPos, 14f))

            return
        } else {
            // asegurarse que el botón confirmar esté oculto fuera de modo selección
            findViewById<Button>(R.id.button_confirm_location).visibility = View.GONE
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

        // tamaño en dp para las miniaturas
        val sizeDp = 100
        val sizePx = (sizeDp * resources.displayMetrics.density).toInt()
        for (uriStr in memory.imageUris) {
            try {
                val iv = ImageView(this)
                val lp = LinearLayout.LayoutParams(sizePx, sizePx)
                lp.setMargins((8 * resources.displayMetrics.density).toInt(),
                    (8 * resources.displayMetrics.density).toInt(),
                    (8 * resources.displayMetrics.density).toInt(),
                    (8 * resources.displayMetrics.density).toInt())
                iv.layoutParams = lp
                iv.scaleType = ImageView.ScaleType.CENTER_CROP
                // usar setImageURI (funcionará si el permiso fue persistido en AddMemoryActivity)
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
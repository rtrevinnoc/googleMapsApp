package com.google.codelabs.buildyourfirstmap

import android.app.Activity
import android.app.DatePickerDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.util.*
import android.util.Log
import android.content.ClipData
import android.content.ContentResolver

class AddMemoryActivity : AppCompatActivity() {

    private val REQUEST_PICK_IMAGES = 2001
    private val REQUEST_PICK_LOCATION = 3001
    private val selectedImages = ArrayList<String>()
    private var dateMillis: Long = System.currentTimeMillis()
    private var lat: Double = 0.0
    private var lng: Double = 0.0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_memory)

        lat = intent.getDoubleExtra("lat", 0.0)
        lng = intent.getDoubleExtra("lng", 0.0)

        val titleInput = findViewById<EditText>(R.id.input_title)
        val descInput = findViewById<EditText>(R.id.input_description)
        val dateText = findViewById<TextView>(R.id.text_date)
        val pickBtn = findViewById<Button>(R.id.button_pick_images)
        val saveBtn = findViewById<Button>(R.id.button_save)
        val imagesCount = findViewById<TextView>(R.id.text_images_count)

        // nuevo: boton para elegir ubicación en mapa y mostrar coordenadas
        val pickLocationBtn = findViewById<Button>(R.id.button_pick_location)
        val locationText = findViewById<TextView>(R.id.text_location)
        locationText.text = "Lat: %.5f, Lng: %.5f".format(lat, lng)

        pickLocationBtn.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java).apply {
                putExtra("pick_location", true)
                // Pasar las coordenadas actuales del formulario como posición inicial
                putExtra("initial_lat", lat)
                putExtra("initial_lng", lng)
            }
            startActivityForResult(intent, REQUEST_PICK_LOCATION)
        }

        // Inicializar fecha por defecto (hoy)
        val calendar = Calendar.getInstance()
        dateMillis = calendar.timeInMillis
        dateText.text = android.text.format.DateFormat.getDateFormat(this).format(Date(dateMillis))

        dateText.setOnClickListener {
            val c = Calendar.getInstance()
            DatePickerDialog(this, { _, y, m, d ->
                val cal = Calendar.getInstance()
                cal.set(y, m, d, 0, 0, 0)
                dateMillis = cal.timeInMillis
                dateText.text = android.text.format.DateFormat.getDateFormat(this).format(Date(dateMillis))
            }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show()
        }

        pickBtn.setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "image/*"
                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                // permitir persistir permiso de lectura
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            }
            startActivityForResult(Intent.createChooser(intent, "Seleccionar imágenes"), REQUEST_PICK_IMAGES)
        }

        saveBtn.setOnClickListener {
            val data = Intent().apply {
                putExtra("title", titleInput.text.toString())
                putExtra("description", descInput.text.toString())
                putExtra("dateMillis", dateMillis)
                putExtra("lat", lat)
                putExtra("lng", lng)
                putStringArrayListExtra("images", selectedImages)
            }
            setResult(Activity.RESULT_OK, data)
            finish()
        }

        // asegurar contador inicial
        imagesCount.text = "Fotos seleccionadas: ${selectedImages.size}"
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_PICK_IMAGES && resultCode == Activity.RESULT_OK && data != null) {
            selectedImages.clear()

            // Una URI única
            data.data?.let { uri ->
                try {
                    contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } catch (e: Exception) {
                    // some providers may not allow persist; ignore
                }
                selectedImages.add(uri.toString())
            }

            // Muchas URIs (multiple)
            val clip = data.clipData
            if (clip != null) {
                for (i in 0 until clip.itemCount) {
                    val uri = clip.getItemAt(i).uri
                    try {
                        contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    } catch (e: Exception) {
                        // ignore
                    }
                    selectedImages.add(uri.toString())
                }
            }

            try {
                val countView = findViewById<TextView>(R.id.text_images_count)
                countView.text = "Fotos seleccionadas: ${selectedImages.size}"
            } catch (e: Exception) {
                Log.w("AddMemoryActivity","text_images_count no encontrado")
            }
        } else if (requestCode == REQUEST_PICK_LOCATION && resultCode == Activity.RESULT_OK && data != null) {
            val pickedLat = data.getDoubleExtra("picked_lat", 0.0)
            val pickedLng = data.getDoubleExtra("picked_lng", 0.0)
            lat = pickedLat
            lng = pickedLng
            try {
                val locationText = findViewById<TextView>(R.id.text_location)
                locationText.text = "Lat: %.5f, Lng: %.5f".format(lat, lng)
            } catch (e: Exception) {
                Log.w("AddMemoryActivity","text_location no encontrado")
            }
        }
    }
}

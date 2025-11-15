package com.fcfm.agosto.aplicacionesmoviles

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.widget.Button
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import androidx.lifecycle.lifecycleScope
import com.fcfm.agosto.aplicacionesmoviles.place.Place
import com.fcfm.agosto.aplicacionesmoviles.place.PlacesReader
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.widget.EditText
import android.widget.NumberPicker
import android.widget.TextView
import com.fcfm.agosto.aplicacionesmoviles.scores.Score
import com.fcfm.agosto.aplicacionesmoviles.scores.ScoresReader
import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class MainActivity : AppCompatActivity() {

    private val places: MutableList<Place> = mutableListOf()
    private lateinit var auth: FirebaseAuth
    private lateinit var credentialManager: CredentialManager
    private var user: FirebaseUser? = null
    private var placesReader = PlacesReader(this@MainActivity);

    private val db = Firebase.firestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        auth = FirebaseAuth.getInstance()
        credentialManager = CredentialManager.create(this);

        findViewById<Button>(R.id.signIn).setOnClickListener {
            if (auth.currentUser != null) {
                startActivity(Intent(this, DetalleDeUsuario::class.java))
            } else {
                signIn()
            }
        }

        loadPlacesAndMap()
    }

    private fun loadPlacesAndMap() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val placesList = placesReader.read()

                places.clear()
                places.addAll(placesList)
                withContext(Dispatchers.Main) {
                    setupMap()
                }
            } catch (e: Exception) {
                Log.e("Firestore", "Error loading places", e)
            }
        }
    }

    private fun setupMap() {
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as? SupportMapFragment

        mapFragment?.getMapAsync { map ->
            map.setOnMapLongClickListener { latLng ->
                val view = LayoutInflater.from(this).inflate(R.layout.new_place_form, null)
                val newTitle = view.findViewById<EditText>(R.id.new_title)
                val newAddress = view.findViewById<EditText>(R.id.new_address)
                val newRatingSelector = view.findViewById<NumberPicker>(R.id.new_rating)
                newRatingSelector.maxValue = 5
                newRatingSelector.minValue = 0

                AlertDialog.Builder(this)
                    .setTitle("New Place")
                    .setView(view)
                    .setPositiveButton("Agregar") { _, _, ->
                        val title = newTitle.text.toString().ifBlank { "Default Title" }
                        val address = newAddress.text.toString().ifBlank { "Default Address" }
                        val rating = newRatingSelector.value.toFloat();

                        placesReader.addPlace(title, latLng, address, rating);

                        lifecycleScope.launch(Dispatchers.IO) {
                            try {
                                val placesList = placesReader.read()
                                places.clear()
                                places.addAll(placesList)
                                withContext(Dispatchers.Main) {
                                    addMarkers(map)
                                }
                            } catch (e: Exception) {
                                Log.e("Firestore", "Error loading places", e)
                            }
                        }
                    }
                    .setNegativeButton("Cancelar", null)
                    .show()

            }

            map.setOnMarkerClickListener { marker ->
                val place = marker.tag as? Place ?: return@setOnMarkerClickListener false
                val view = LayoutInflater.from(this).inflate(R.layout.marker_popup, null)
                val title = view.findViewById<TextView>(R.id.marker_popup_title)
                title.text = place.name
                val address = view.findViewById<TextView>(R.id.marker_popup_address)
                address.text = place.address
                val newRatingSelector = view.findViewById<NumberPicker>(R.id.marker_popup_rating)
                newRatingSelector.maxValue = 5
                newRatingSelector.minValue = 0
                newRatingSelector.value = place.rating.toInt()

                AlertDialog.Builder(this)
                    .setTitle("Place Information")
                    .setView(view)
                    .setPositiveButton("Editar") { _, _, ->
                        val rating = newRatingSelector.value.toFloat();

                        if (auth.currentUser != null) {
                            val currentTime =
                                SimpleDateFormat("yyyy-mm-dd hh:mm:ss", Locale.getDefault())
                            currentTime.timeZone = TimeZone.getTimeZone("UTC")
                            val currentTimeInUTC = currentTime.format(Date())
                            val score =
                                Score(place.id, auth.currentUser?.email!!, rating, currentTimeInUTC)

                            lifecycleScope.launch(Dispatchers.IO) {
                                val scoresReader = ScoresReader(this@MainActivity)
                                val newScore = scoresReader.doPost(score)
                                withContext(Dispatchers.Main) {
                                    place.rating = newScore
                                }
                            }

                        }
                        newRatingSelector.value = place.rating.toInt()

                        db.collection(getString(R.string.placesFirestore)).document(place.id).set(place)
                    }
                    .setNegativeButton("Cancelar", null)
                    .show()

                true
            }

            addMarkers(map)
            //map.setInfoWindowAdapter(MarkerPopupAdapter(this))
        }
    }

    private fun addMarkers(map: GoogleMap) {
        map.clear()
        places.forEach { place ->
            val marker = map.addMarker(
                MarkerOptions()
                    .title(place.name)
                    .position(place.latLng)
            )
            marker?.tag = place
        }
    }

    private fun signInWithGoogle(tokenId: String) {
        val credential = GoogleAuthProvider.getCredential(tokenId, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    user = auth.currentUser
                    Log.i("AUTH", "Google Sign-In successful: ${user?.email}")
                    startActivity(Intent(this, DetalleDeUsuario::class.java))
                } else {
                    Log.w("AUTH", "Google Sign-In failed", task.exception)
                }
            }
    }

    private fun signIn() {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val googleIdOption = GetGoogleIdOption.Builder()
                    .setFilterByAuthorizedAccounts(false)
                    .setServerClientId(getString(R.string.default_web_client_id))
                    .build();

                val request = GetCredentialRequest.Builder()
                    .addCredentialOption(googleIdOption)
                    .build();

                val result = credentialManager.getCredential(this@MainActivity, request);

                val credential = result.credential;
                val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data);
                signInWithGoogle(googleIdToken.idToken);
            } catch (e: GetCredentialException) {
                Log.w("AUTH", "Credential error", e)
            }
        }
    }
}
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

class MainActivity : AppCompatActivity() {

    private val places: MutableList<Place> = mutableListOf()
    private lateinit var auth: FirebaseAuth
    private lateinit var credentialManager: CredentialManager
    private var user: FirebaseUser? = null
    private var placesReader = PlacesReader(this@MainActivity);

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
                setupMap()
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
                val newRating = view.findViewById<EditText>(R.id.new_rating)

                AlertDialog.Builder(this)
                    .setTitle("New Place")
                    .setView(view)
                    .setPositiveButton("Agregar") { _, _, ->
                        val title = newTitle.text.toString().ifBlank { "Default Title" }
                        val address = newAddress.text.toString().ifBlank { "Default Address" }
                        val rating = newRating.text.toString().ifBlank { "0.0" } .toFloat()

                        placesReader.addPlace(title, latLng, address, rating);

                        lifecycleScope.launch(Dispatchers.IO) {
                            try {
                                val placesList = placesReader.read()
                                places.clear()
                                places.addAll(placesList)
                            } catch (e: Exception) {
                                Log.e("Firestore", "Error loading places", e)
                            }
                        }
                    }
                    .setNegativeButton("Cancelar", null)
                    .show()

            }

            addMarkers(map)
            map.setInfoWindowAdapter(MarkerPopupAdapter(this))
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
                    .setServerClientId(R.string.default_web_client_id.toString())
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
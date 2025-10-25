package com.fcfm.agosto.aplicacionesmoviles

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.fcfm.agosto.aplicacionesmoviles.place.Place
import com.fcfm.agosto.aplicacionesmoviles.place.PlacesReader
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.MarkerOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private val places: MutableList<Place> = mutableListOf()
    private lateinit var auth: FirebaseAuth
    private lateinit var client: GoogleSignInClient
    private var user: FirebaseUser? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        auth = FirebaseAuth.getInstance()
        setupGoogleSignIn()

        findViewById<Button>(R.id.signIn).setOnClickListener {
            if (auth.currentUser != null) {
                startActivity(Intent(this, DetalleDeUsuario::class.java))
            } else {
                signIn()
            }
        }

        loadPlacesAndMap()
    }

    private fun setupGoogleSignIn() {
        val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        client = GoogleSignIn.getClient(this, options)
    }

    private fun loadPlacesAndMap() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val placesList = PlacesReader(this@MainActivity).read()

                withContext(Dispatchers.Main) {
                    if (!isDestroyed) {
                        places.clear()
                        places.addAll(placesList)
                        setupMap()
                    }
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

    private val signInHandler = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        try {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            val account = task.getResult(Exception::class.java)
            account?.idToken?.let { signInWithGoogle(it) }
                ?: Log.w("AUTH", "Google Sign-In failed: No ID Token")
        } catch (e: Exception) {
            Log.e("AUTH", "Google Sign-In error", e)
        }
    }

    private fun signIn() {
        signInHandler.launch(client.signInIntent)
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
}
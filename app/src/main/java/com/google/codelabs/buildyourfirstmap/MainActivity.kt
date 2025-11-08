// Copyright 2020 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.google.codelabs.buildyourfirstmap

import android.content.Intent
import com.google.firebase.auth.FirebaseAuth


import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.MarkerOptions
import android.util.Log
import android.view.LayoutInflater
import android.widget.Button
import android.widget.EditText
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.credentials.CredentialManager
import androidx.lifecycle.lifecycleScope
import com.google.codelabs.buildyourfirstmap.DetalleDeUsuario
import com.google.codelabs.buildyourfirstmap.MarkerPopupAdapter
import com.google.codelabs.buildyourfirstmap.R
import com.google.codelabs.buildyourfirstmap.place.PlacesReader
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.Dispatcher

class MainActivity : AppCompatActivity() {
    private lateinit var auth: FirebaseAuth
    //private lateinit var client = GoogleSignInClient
    private val places: MutableList<com.fcfm.agosto.aplicacionesmoviles.place.Place> = mutableListOf()
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


  /*      val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(true)
            .setServerClientId(baseContext.getString(R.string.default_web_client_id))
            .build()*/

   /*     val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
            firebaseAuthWithGoogle(googleIdTokenCredential.idToken)
        } else {
            Log.w(TAG, "Credential is not of type Google ID!")
        }*/

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

    private fun addMarkers(map: GoogleMap) {
        places.forEach { place ->
            val marker = map.addMarker(
                MarkerOptions()
                    .title(place.name)
                    .position(place.latLng)
            )

            marker?.tag = place
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

    private val signIntentHandler = registerForActivityResult(
        contact = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val account = GoogleSignIn.getSignedInAccountFromIntent( data = result.data)
        val account = accountIntent.getResult()
        signInWithGoogle(tokenId = account.idToken!!)

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

                val request = androidx.credentials.GetCredentialRequest.Builder()
                    .addCredentialOption(googleIdOption)
                    .build();

                val result = credentialManager.getCredential(this@MainActivity, request);

                val credential = result.credential;
                val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data);
                signInWithGoogle(googleIdToken.idToken);
            } catch (e: androidx.credentials.exceptions.GetCredentialException) {
                Log.w("AUTH", "Credential error", e)
            }
        }
    }

}

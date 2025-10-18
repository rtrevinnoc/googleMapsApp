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

package com.fcfm.agosto.aplicacionesmoviles

import android.content.ContentValues.TAG
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.fcfm.agosto.aplicacionesmoviles.place.Place
import com.fcfm.agosto.aplicacionesmoviles.place.PlacesReader
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.fcfm.agosto.aplicacionesmoviles.R
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider

class MainActivity : AppCompatActivity() {
    private val places: List<Place> by lazy {
        PlacesReader(this).read()
    }
    private lateinit var auth: FirebaseAuth
    private lateinit var client: GoogleSignInClient
    private lateinit var user: FirebaseUser

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        auth = FirebaseAuth.getInstance()

        val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()

        client = GoogleSignIn.getClient(this, options)

        findViewById<Button>(R.id.signIn).setOnClickListener {
            if (auth.currentUser != null) {
                val userDetail = Intent(this, DetalleDeUsuario::class.java)
                startActivity(userDetail)
            } else {
                signIn()
            }
        }

        val mapFragment = supportFragmentManager.findFragmentById(R.id.map)  as? SupportMapFragment
        mapFragment?.getMapAsync { map ->
            addMarkers(map)
            map.setInfoWindowAdapter(MarkerPopupAdapter(this))
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

    private val signInHandler = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val accountIntent = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        val account = accountIntent.getResult()
        signInWithGoogle(account.idToken!!)
    }

    private fun signIn() {
        val intent = client.signInIntent
        signInHandler.launch(intent)
    }

    private fun signInWithGoogle(tokenId: String) {
        val credential = GoogleAuthProvider.getCredential(tokenId, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    user = auth.currentUser!!
                } else {
                    Log.w("AUTH", "FAIL")
                }
            }
    }
}

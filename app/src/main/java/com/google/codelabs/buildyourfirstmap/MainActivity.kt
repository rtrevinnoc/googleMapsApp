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

import com.google.firebase.auth.FirebaseAuth


import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.MarkerOptions
import com.google.codelabs.buildyourfirstmap.place.Place
import com.google.codelabs.buildyourfirstmap.place.PlacesReader
import com.google.firebase.Firebase
import android.credentials.GetCredentialRequest
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.firebase.auth.FirebaseUser

class MainActivity : AppCompatActivity() {
    private val places: List<Place> by lazy {
        PlacesReader(this).read()
    }
    private lateinit var auth: FirebaseAuth
    private lateinit var client = GoogleSignInClient

    private lateinit var user : FirebaseUser?


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val auth = FirebaseAuth.getInstance()

        val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(resId = R.string.default_web_client_id))
            .requestEmail()
            .build()

        val client = GoogleSignIN.getClient(activity = this, options)

        findViewById<Button>(id = R.id.signIn).setOnClickListener {
            signIn()
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

        val mapFragment = supportFragmentManager.findFragmentById(R.id.sampleMap)  as? SupportMapFragment
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

    private val signIntentHandler = registerForActivityResult(
        contact = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val account = GoogleSignIn.getSignedInAccountFromIntent( data = result.data)
        val account = accountIntent.getResult()
        signInWithGoogle(tokenId = account.idToken!!)

    }


    private fun signIn(){
        val intent = client.signInIntent
        signInHandler.launch(input = intent)
    }

    private fun signInWithGoogle(tokenId: String) {
        val credential = GoogleAuthProvider.getCredential(p0 = tokenId, p1 = null)
        auth.signInWithCredential(p0 = credential)
            .addOnCompleteListener( p0 =this){ task ->
                if (task.isSuccesful){
                    user = auth.currentUser
                }else{
                    Log.w(tag = "AUTH", msg = "FAIL")
                }
            }

    }

}

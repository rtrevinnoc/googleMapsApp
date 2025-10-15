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

import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentManager
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.codelabs.buildyourfirstmap.place.Place
import com.google.codelabs.buildyourfirstmap.place.PlacesReader
import java.util.jar.Manifest
import android.widget.Toast

class MainActivity : AppCompatActivity(), OnMapReadyCallback, GoogleMap.OnMyLocationButtonClickListener, GoogleMap.OnMyLocationClickListener{
    private lateinit var map: GoogleMap

    companion object{
        const val REQUEST_CODE_LOCATION=0
    }
    private val places: List<Place> by lazy {
        PlacesReader(this).read()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        createFragment()

        /*val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(true)
            .setServerClientId(baseContext.getString(R.string.default_web_client_id))
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()*/
    }

    private fun createFragment() {
        val mapFragment= supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)
    }


    override fun onMapReady(googleMap: GoogleMap) {
        map=googleMap
        createMarker()
        map.setOnMyLocationButtonClickListener(this)
        map.setOnMyLocationClickListener (this)
        enableLocation()
    }

    private fun createMarker(){
        val coordenadas = LatLng(25.725820578293273, -100.31521046091045)
        val marker = MarkerOptions().position(coordenadas).title("FCFM")
        map.addMarker(marker)
        map.animateCamera(
            CameraUpdateFactory.newLatLngZoom(coordenadas,12f),2000,null
        )
    }

    private fun isLocationPermissionGranted()= ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun enableLocation(){
        if(!::map.isInitialized) return
        if(isLocationPermissionGranted()){
            map.isMyLocationEnabled=true
        }else{
            requestLocationPermission()
        }
    }

    private fun requestLocationPermission(){
        if(ActivityCompat.shouldShowRequestPermissionRationale(this, android.Manifest.permission.ACCESS_FINE_LOCATION)&& ActivityCompat.shouldShowRequestPermissionRationale(this, android.Manifest.permission.ACCESS_COARSE_LOCATION)){
            Toast.makeText(this,"Acepte los permisos",Toast.LENGTH_SHORT).show()
        }else{
            ActivityCompat.requestPermissions(this,arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION),REQUEST_CODE_LOCATION)
            ActivityCompat.requestPermissions(this,arrayOf(android.Manifest.permission.ACCESS_COARSE_LOCATION),REQUEST_CODE_LOCATION)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String?>,
        grantResults: IntArray,
        deviceId: Int
    ) {
        when(requestCode){
            REQUEST_CODE_LOCATION->if(grantResults.isNotEmpty()&&grantResults[0]== PackageManager.PERMISSION_GRANTED){
                map.isMyLocationEnabled=true
            }else{
                Toast.makeText(this,"Acepte los permisos en los ajustes!",Toast.LENGTH_SHORT).show()
            }
            else -> {}
        }
    }

    override fun onResumeFragments() {
        super.onResumeFragments()
        if(!::map.isInitialized)return
        if(!isLocationPermissionGranted()){
            map.isMyLocationEnabled=false
            Toast.makeText(this,"Acepte los permisos en los ajustes ya!",Toast.LENGTH_SHORT).show()
        }
    }

    override fun onMyLocationButtonClick(): Boolean {
        Toast.makeText(this,"Boton pulsado",Toast.LENGTH_SHORT).show()
        return false
    }

    override fun onMyLocationClick(p0: Location) {
        Toast.makeText(this,"Ubicacion actual: ${p0.latitude}, ${p0.longitude}",Toast.LENGTH_SHORT).show()
    }


}

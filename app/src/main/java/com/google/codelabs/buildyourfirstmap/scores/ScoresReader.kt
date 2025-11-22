package com.google.codelabs.buildyourfirstmap.scores

import android.content.Context
import android.util.Log
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext


class ScoresReader(private val context: Context) {

    private val sheetUrl = "https://script.google.com/macros/s/AKfycbxTjpJnduKy_dnATnj8Azytm6BfUo_TnnkhDNYzN4VExM72MEjVZ2IRohkmZYms0ah5/exec"

    private val client = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun getAverageScoreFor(placeName: String): Float = withContext(Dispatchers.IO) {
        val url = "$sheetUrl?placeId=${placeName.trim()}" // tu script usa ?placeId=
        Log.d("ScoresReader", "Consultando promedio para: $placeName")
        val request = Request.Builder().url(url).build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e("ScoresReader", "Error al obtener promedio: ${response.code}")
                    return@withContext 0f
                }

                val body = response.body?.string()?.trim()
                Log.d("ScoresReader", "Promedio leído: $body")
                body?.toFloatOrNull() ?: 0f
            }
        } catch (e: IOException) {
            Log.e("ScoresReader", "Error de red (GET): ${e.message}")
            0f
        }
    }


    fun doPost(score: Score): Float {
        val jsonString = json.encodeToString(score)
        val body = jsonString.toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url(sheetUrl)
            .post(body)
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e("ScoresReader", "Error al enviar score: ${response.code}")
                    return 0f
                }
                val result = response.body?.string()?.trim()
                Log.d("ScoresReader", "Nuevo promedio devuelto: $result")
                result?.toFloatOrNull() ?: 0f
            }
        } catch (e: IOException) {
            Log.e("ScoresReader", "Error de red (POST): ${e.message}")
            0f
        }
    }
}
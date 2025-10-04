package com.fcfm.agosto.aplicacionesmoviles.scores

import android.content.Context
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody

class ScoresReader(private val context: Context) {

    private val sheetUrl = "https://script.google.com/macros/s/AKfycbz2FoM2a0wCrreUGzvVcca9IkavoZlPk7Ifn2nFXscSx1MX3LNokO9QgGbTt1lFL-DG/exec"
    private val client = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }

    fun doGet(): List<Score> {
        val request = Request.Builder()
            .url(sheetUrl)
            .build()

        client.newCall(request).execute().use { response ->
            return json.decodeFromString<List<Score>>(response.body.toString())
        }
    }

    fun doPost(score: Score): ResponseBody {
        val jsonString = json.encodeToString<Score>(score)
        val body = jsonString.toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url(sheetUrl)
            .post(body)
            .build()

        client.newCall(request).execute().use { response ->
            return response.body
        }
    }
}
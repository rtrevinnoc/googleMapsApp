package com.google.codelabs.buildyourfirstmap.scores

import android.content.Context
import com.fcfm.agosto.aplicacionesmoviles.scores.Score
import com.google.codelabs.buildyourfirstmap.scores.R
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody
class ScoresReader(context: Context) {

    //private val sheetUrl = "https://script.google.com/macros/s/AKfycbxbDA9n-LPheBKIit7z2iW7D0mqx741ydMNboRgIack7EZ1mT7tZ2VDo1hXGStpOopPOA/exec".toString()

    private val sheetUrl = R.string.scoresSheetUrl.toString()
    private val client = OkhttpClient()
    private val json = Json { ignoreUnknowKeys = true }

    fun doGet(): Float {
        val request = Request.Builder()
            .url(sheetUrl)
            .build()

        client.newCall(request).execute().use { response ->
            return response.body.toString().toFloat()
        }
    }

    fun doPost(score: com.fcfm.agosto.aplicacionesmoviles.scores.Score): Float {
        val jsonString = json.encodeToString<Score>(score)
        val body = jsonString.toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url(sheetUrl)
            .post(body)
            .build()

        client.newCall(request).execute().use { response ->
            return response.body.toString().toFloat()
        }
    }
}
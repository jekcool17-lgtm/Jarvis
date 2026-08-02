package com.jarvis.assistant

import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class AiFallbackHelper {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    // Замените на адрес своего прокси-сервера, который проксирует запрос
    // на https://api.anthropic.com/v1/messages, подставляя серверный ключ.
    private val PROXY_URL = "https://YOUR-PROXY-SERVER.example.com/ask"

    fun ask(question: String): String {
        return try {
            val body = JSONObject().apply {
                put("question", question)
            }
            val request = Request.Builder()
                .url(PROXY_URL)
                .post(body.toString().toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return "Не удалось получить ответ, сэр"
                val json = JSONObject(resp.body?.string().orEmpty())
                json.optString("answer", "Не удалось получить ответ, сэр")
            }
        } catch (e: IOException) {
            "Не удалось связаться с сервером"
        }
    }
}

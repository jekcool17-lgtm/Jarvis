package com.jarvis.assistant

import okhttp3.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Когда сказанная фраза не распознана как команда управления телефоном,
 * мы трактуем её как обычный вопрос ("Джарвис, какая столица Франции?")
 * и отправляем в Anthropic API, чтобы получить содержательный ответ,
 * как в фильме.
 *
 * ВАЖНО (обязательно к прочтению перед сборкой):
 * 1) НЕ храните настоящий API-ключ прямо в коде приложения — его можно
 *    вытащить из APK декомпиляцией. Для реального использования сделайте
 *    небольшой backend-прокси (например, Cloudflare Worker) который
 *    держит ключ у себя и просто пробрасывает запрос. Здесь для
 *    простоты старта ключ читается как заглушка — замените на свой
 *    прокси-URL в PROXY_URL.
 * 2) Вызов идёт синхронно в текущей реализации ради простоты — для
 *    продакшена оберните в корутину/callback, чтобы не блокировать поток.
 */
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
                .post(RequestBody.create(
                    MediaType.parse("application/json"), body.toString()
                ))
                .build()

            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return "Не удалось получить ответ, сэр"
                val json = JSONObject(resp.body()?.string().orEmpty())
                json.optString("answer", "Не удалось получить ответ, сэр")
            }
        } catch (e: IOException) {
            "Не удалось связаться с сервером"
        }
    }
}

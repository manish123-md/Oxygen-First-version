package com.oxygen.assistant

import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.Executors

/**
 * Bina kisi paid API key ke internet se jawab dhoondta hai.
 * DuckDuckGo ka "Instant Answer" API free hai, key nahi maangta.
 * Agar wahan jawab na mile to Wikipedia REST summary try karta hai.
 */
object SearchHelper {

    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    fun search(query: String, onResult: (String) -> Unit) {
        if (query.isBlank()) {
            onResult("Aapne kya poochha, thoda clear bolenge?")
            return
        }
        executor.execute {
            val answer = tryDuckDuckGo(query) ?: tryWikipedia(query)
                ?: "Maaf kijiye, iska jawab abhi nahi mil paaya."
            mainHandler.post { onResult(answer) }
        }
    }

    private fun tryDuckDuckGo(query: String): String? {
        return try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val url = URL("https://api.duckduckgo.com/?q=$encoded&format=json&no_html=1&skip_disambig=1")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            val body = conn.inputStream.bufferedReader().readText()
            val json = JSONObject(body)
            val abstractText = json.optString("AbstractText")
            if (abstractText.isNotBlank()) abstractText else null
        } catch (e: Exception) {
            null
        }
    }

    private fun tryWikipedia(query: String): String? {
        return try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val url = URL("https://en.wikipedia.org/api/rest_v1/page/summary/$encoded")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            if (conn.responseCode != 200) return null
            val body = conn.inputStream.bufferedReader().readText()
            val json = JSONObject(body)
            json.optString("extract").ifBlank { null }
        } catch (e: Exception) {
            null
        }
    }
}

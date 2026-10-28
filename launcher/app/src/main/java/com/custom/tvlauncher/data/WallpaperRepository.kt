package com.custom.tvlauncher.data

import com.custom.tvlauncher.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

class WallpaperRepository {

    /** URL d'une nouvelle photo de nature 1920x1080 (Unsplash, ou picsum.photos si aucune clé). */
    suspend fun nextUrl(): String? = withContext(Dispatchers.IO) {
        val key = BuildConfig.UNSPLASH_ACCESS_KEY
        try {
            if (key.isBlank()) {
                return@withContext "https://picsum.photos/seed/${System.currentTimeMillis()}/1920/1080"
            }
            val api = "https://api.unsplash.com/photos/random" +
                "?query=nature%20landscape&orientation=landscape&content_filter=high&client_id=$key"
            val raw = JSONObject(Net.get(api)).getJSONObject("urls").getString("raw")
            "$raw&w=1920&h=1080&fit=crop&q=80"
        } catch (e: Exception) {
            null
        }
    }
}

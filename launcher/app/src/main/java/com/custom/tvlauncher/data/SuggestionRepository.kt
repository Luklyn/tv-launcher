package com.custom.tvlauncher.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.custom.tvlauncher.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Suggestions par service : TMDB (films + séries populaires par plateforme, France) et YouTube (tendances). */
class SuggestionRepository(private val ctx: Context) {

    suspend fun fetchAll(): Map<Service, List<Suggestion>> = withContext(Dispatchers.IO) {
        Service.values().associateWith { service ->
            val list = try {
                if (service == Service.YOUTUBE) youtube() else tmdb(service.tmdbProvider!!)
            } catch (e: Exception) {
                Log.e(TAG, "${service.title} : ${e.javaClass.simpleName} ${e.message}")
                emptyList()
            }
            Log.d(TAG, "${service.title} : ${list.size} suggestions")
            list
        }
    }

    private fun tmdb(provider: Int): List<Suggestion> {
        val key = BuildConfig.TMDB_API_KEY
        if (key.isBlank()) return emptyList()
        val perType = listOf("movie", "tv").map { type ->
            val url = "https://api.themoviedb.org/3/discover/$type?api_key=$key" +
                "&language=fr-FR&watch_region=FR&with_watch_providers=$provider" +
                "&with_watch_monetization_types=flatrate&sort_by=popularity.desc&page=1"
            val results = JSONObject(Net.get(url)).getJSONArray("results")
            buildList {
                for (i in 0 until results.length()) {
                    val o = results.getJSONObject(i)
                    if (o.isNull("backdrop_path")) continue
                    val title = o.optString("title").ifBlank { o.optString("name") }
                    if (title.isBlank()) continue
                    add(
                        Suggestion(
                            id = "$type-${o.getLong("id")}",
                            title = title,
                            imageUrl = "https://image.tmdb.org/t/p/w500" + o.getString("backdrop_path"),
                            intent = null,
                        )
                    )
                }
            }
        }
        // Alterne films et séries, 12 maximum
        val out = mutableListOf<Suggestion>()
        var i = 0
        while (out.size < 12 && perType.any { i < it.size }) {
            perType.forEach { l -> if (i < l.size && out.size < 12) out += l[i] }
            i++
        }
        return out
    }

    private fun youtube(): List<Suggestion> {
        val key = BuildConfig.YOUTUBE_API_KEY
        if (key.isBlank()) {
            Log.w(TAG, "YOUTUBE_API_KEY vide : ajoute-la dans local.properties puis Sync Gradle + recompile")
            return emptyList()
        }
        val url = "https://www.googleapis.com/youtube/v3/videos?part=snippet&chart=mostPopular" +
            "&regionCode=FR&maxResults=12&key=$key"
        val items = JSONObject(Net.get(url)).getJSONArray("items")
        val pm = ctx.packageManager
        val ytPackage = listOf("com.google.android.youtube.tv", "com.google.android.youtube")
            .firstOrNull { runCatching { pm.getPackageInfo(it, 0) }.isSuccess }
        return buildList {
            for (i in 0 until items.length()) {
                val o = items.getJSONObject(i)
                val id = o.getString("id")
                val sn = o.getJSONObject("snippet")
                val thumbs = sn.getJSONObject("thumbnails")
                val thumb = (thumbs.optJSONObject("medium") ?: thumbs.optJSONObject("high") ?: thumbs.optJSONObject("default"))
                    ?.optString("url").orEmpty()
                if (thumb.isBlank()) continue
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=$id"))
                    .apply { ytPackage?.let { setPackage(it) } }
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                add(Suggestion(id = "yt-$id", title = sn.getString("title"), imageUrl = thumb, intent = intent))
            }
        }
    }

    private companion object {
        const val TAG = "Suggest"
    }
}

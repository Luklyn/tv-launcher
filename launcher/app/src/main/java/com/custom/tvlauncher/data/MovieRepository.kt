package com.custom.tvlauncher.data

import android.content.Context
import android.util.Log
import com.custom.tvlauncher.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MovieRepository(ctx: Context) {

    private val prefs = ctx.getSharedPreferences("canal_seen", Context.MODE_PRIVATE)

    /** Films inclus dans l'abonnement Canal+ (TMDB, fournisseur 381), les plus récemment apparus d'abord. */
    suspend fun fetchCanalNew(): List<Movie> = withContext(Dispatchers.IO) {
        val key = BuildConfig.TMDB_API_KEY
        if (key.isBlank()) {
            Log.w(TAG, "TMDB_API_KEY vide : ajoute-la dans local.properties puis Sync Gradle + recompile")
            return@withContext emptyList()
        }
        try {
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
            val all = mutableListOf<Movie>()
            for (page in 1..3) {
                val url = "https://api.themoviedb.org/3/discover/movie?api_key=$key" +
                    "&language=fr-FR&watch_region=FR&with_watch_providers=381" +
                    "&with_watch_monetization_types=flatrate" +
                    "&sort_by=primary_release_date.desc" +
                    "&primary_release_date.lte=$today&vote_count.gte=20&page=$page"
                val results = JSONObject(Net.get(url)).getJSONArray("results")
                for (i in 0 until results.length()) {
                    val o = results.getJSONObject(i)
                    if (o.isNull("poster_path")) continue
                    all += Movie(
                        id = o.getLong("id"),
                        title = o.getString("title"),
                        posterUrl = "https://image.tmdb.org/t/p/w342" + o.getString("poster_path"),
                        overview = o.optString("overview", ""),
                        releaseDate = o.optString("release_date", ""),
                    )
                }
                if (results.length() < 20) break
            }

            // Date de première apparition de chaque film dans la liste Canal+ (TMDB ne donne pas la date de mise en ligne)
            val now = System.currentTimeMillis()
            val editor = prefs.edit()
            val firstSeen = all.associate { m ->
                val k = "id_${m.id}"
                val t = if (prefs.contains(k)) prefs.getLong(k, now) else {
                    editor.putLong(k, now)
                    now
                }
                m.id to t
            }
            editor.apply()

            all.sortedWith(
                compareByDescending<Movie> { firstSeen[it.id] ?: 0L }
                    .thenByDescending { it.releaseDate }
            ).take(12).also { Log.d(TAG, "${it.size} films Canal+ chargés") }
        } catch (e: Exception) {
            Log.e(TAG, "Échec TMDB : ${e.javaClass.simpleName} ${e.message}")
            emptyList()
        }
    }

    private companion object {
        const val TAG = "Canal"
    }
}

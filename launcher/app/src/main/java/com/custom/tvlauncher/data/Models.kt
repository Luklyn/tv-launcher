package com.custom.tvlauncher.data

import android.content.Intent
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap

/** Une application installée, avec ses visuels déjà convertis en bitmaps (partageables entre composables). */
data class AppEntry(
    val packageName: String,
    val label: String,
    val launchIntent: Intent,
    val icon: ImageBitmap,
    val banner: ImageBitmap?,
)

/** Services pour lesquels le launcher propose des suggestions quand on survole leur application. */
enum class Service(val title: String, val subtitle: String, val tmdbProvider: Int?) {
    YOUTUBE("YouTube", "Tendances en France", null),
    PRIME("Prime Video", "Populaires en ce moment", 119),
    APPLE("Apple TV", "Populaires en ce moment", 350),
    CANAL("Canal+", "Populaires en ce moment", 381),
    DISNEY("Disney+", "Populaires en ce moment", 337);

    companion object {
        fun of(app: AppEntry): Service? {
            val l = app.label.lowercase()
            val p = app.packageName.lowercase()
            return when {
                (l.contains("youtube") || p.contains("youtube")) && !l.contains("music") && !l.contains("kids") -> YOUTUBE
                l.contains("prime video") || p.startsWith("com.amazon.amazonvideo") -> PRIME
                l.contains("apple tv") || p.startsWith("com.apple.atve") -> APPLE
                l.contains("canal") -> CANAL
                l.contains("disney") -> DISNEY
                else -> null
            }
        }
    }
}

/** Une suggestion (vignette 16:9). [intent] = null : on ouvre simplement l'application. */
data class Suggestion(
    val id: String,
    val title: String,
    val imageUrl: String,
    val intent: Intent?,
)

data class Weather(
    val tempC: Int,
    val iconCode: String,
    val description: String,
)

/** Un film récemment disponible sur Canal+ (données TMDB). */
data class Movie(
    val id: Long,
    val title: String,
    val posterUrl: String,
    val overview: String,
    val releaseDate: String,
)

/** Prévision d'une journée (météo de la semaine). */
data class DayForecast(
    val label: String,
    val code: Int,
    val max: Int,
    val min: Int,
)

/** Utilisation de la mémoire vive et du stockage. */
data class SystemInfo(
    val ramFreeMb: Long = 0,
    val ramTotalMb: Long = 1,
    val storageFreeGb: Double = 0.0,
    val storageTotalGb: Double = 1.0,
) {
    val ramUsed: Float get() = (1f - ramFreeMb.toFloat() / ramTotalMb).coerceIn(0f, 1f)
    val storageUsed: Float get() = (1f - (storageFreeGb / storageTotalGb).toFloat()).coerceIn(0f, 1f)
}

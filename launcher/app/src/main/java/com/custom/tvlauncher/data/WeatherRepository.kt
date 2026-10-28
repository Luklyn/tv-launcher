package com.custom.tvlauncher.data

import com.custom.tvlauncher.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

class WeatherRepository {

    suspend fun fetch(): Weather? = withContext(Dispatchers.IO) {
        val key = BuildConfig.OPENWEATHER_API_KEY
        if (key.isBlank()) return@withContext null
        try {
            val url = "https://api.openweathermap.org/data/2.5/weather" +
                "?lat=${BuildConfig.WEATHER_LAT}&lon=${BuildConfig.WEATHER_LON}" +
                "&units=metric&lang=fr&appid=$key"
            val json = JSONObject(Net.get(url))
            val w = json.getJSONArray("weather").getJSONObject(0)
            Weather(
                tempC = json.getJSONObject("main").getDouble("temp").roundToInt(),
                iconCode = w.getString("icon"),
                description = w.getString("description"),
            )
        } catch (e: Exception) {
            null
        }
    }

    /** Prévisions sur 7 jours via Open-Meteo (gratuit, sans clé). */
    suspend fun fetchWeek(): List<DayForecast> = withContext(Dispatchers.IO) {
        try {
            val url = "https://api.open-meteo.com/v1/forecast" +
                "?latitude=${BuildConfig.WEATHER_LAT}&longitude=${BuildConfig.WEATHER_LON}" +
                "&daily=weathercode,temperature_2m_max,temperature_2m_min&timezone=auto&forecast_days=7"
            val daily = JSONObject(Net.get(url)).getJSONObject("daily")
            val days = daily.getJSONArray("time")
            val codes = daily.getJSONArray("weathercode")
            val max = daily.getJSONArray("temperature_2m_max")
            val min = daily.getJSONArray("temperature_2m_min")
            List(days.length()) { i ->
                val name = LocalDate.parse(days.getString(i)).dayOfWeek
                    .getDisplayName(TextStyle.SHORT, Locale.FRANCE).removeSuffix(".")
                    .replaceFirstChar { it.uppercase() }
                DayForecast(
                    label = if (i == 0) "Auj." else name,
                    code = codes.getInt(i),
                    max = max.getDouble(i).roundToInt(),
                    min = min.getDouble(i).roundToInt(),
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
}

/** Code météo WMO (Open-Meteo) vers un pictogramme. */
fun wmoEmoji(code: Int): String = when (code) {
    0 -> "☀️"
    1 -> "🌤️"
    2 -> "⛅"
    3 -> "☁️"
    45, 48 -> "🌫️"
    in 51..57 -> "🌦️"
    in 61..67 -> "🌧️"
    in 71..77 -> "❄️"
    in 80..82 -> "🌦️"
    85, 86 -> "🌨️"
    in 95..99 -> "⛈️"
    else -> "🌡️"
}

fun weatherEmoji(iconCode: String): String = when (iconCode.take(2)) {
    "01" -> if (iconCode.endsWith("n")) "🌙" else "☀️"
    "02" -> if (iconCode.endsWith("n")) "☁️" else "⛅"
    "03", "04" -> "☁️"
    "09", "10" -> "🌧️"
    "11" -> "⛈️"
    "13" -> "❄️"
    "50" -> "🌫️"
    else -> "🌡️"
}

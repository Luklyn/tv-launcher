@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.custom.tvlauncher.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon

/**
 * Icônes météo dessinées par le launcher (les emoji dépendent de la police de la TV et s'affichent parfois
 * comme un carré vide). Rendu identique sur toutes les TV.
 */
enum class Cond { SUN, MOON, PARTLY, CLOUD, RAIN, STORM, SNOW, FOG }

/** Code d'icône OpenWeather ("01d", "10n"…) → condition. */
fun condOf(iconCode: String): Cond = when (iconCode.take(2)) {
    "01" -> if (iconCode.endsWith("n")) Cond.MOON else Cond.SUN
    "02" -> Cond.PARTLY
    "03", "04" -> Cond.CLOUD
    "09", "10" -> Cond.RAIN
    "11" -> Cond.STORM
    "13" -> Cond.SNOW
    "50" -> Cond.FOG
    else -> Cond.CLOUD
}

/** Code météo WMO (Open-Meteo) → condition. */
fun condOfWmo(code: Int): Cond = when (code) {
    0, 1 -> Cond.SUN
    2 -> Cond.PARTLY
    3 -> Cond.CLOUD
    45, 48 -> Cond.FOG
    in 51..67, in 80..82 -> Cond.RAIN
    in 71..77, 85, 86 -> Cond.SNOW
    in 95..99 -> Cond.STORM
    else -> Cond.CLOUD
}

private fun vec(name: String, path: String): ImageVector =
    ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
        .addPath(addPathNodes(path), fill = SolidColor(Color.Black))
        .build()

private val SunVec by lazy {
    vec("sun", "M6.76 4.84l-1.8-1.79-1.41 1.41 1.79 1.79 1.42-1.41zM4 10.5H1v2h3v-2zm9-9.95h-2V3.5h2V.55zm7.45 3.91l-1.41-1.41-1.79 1.79 1.41 1.41 1.79-1.79zm-3.21 13.7l1.79 1.8 1.41-1.41-1.8-1.79-1.4 1.4zM20 10.5v2h3v-2h-3zm-8-5c-3.31 0-6 2.69-6 6s2.69 6 6 6 6-2.69 6-6-2.69-6-6-6zm-1 16.95h2V19.5h-2v2.95zm-7.45-3.91l1.41 1.41 1.79-1.8-1.41-1.41-1.79 1.8z")
}
private val MoonVec by lazy {
    vec("moon", "M12 3c-4.97 0-9 4.03-9 9s4.03 9 9 9 9-4.03 9-9c0-.46-.04-.92-.1-1.36-.98 1.37-2.58 2.26-4.4 2.26-2.98 0-5.4-2.42-5.4-5.4 0-1.81.89-3.42 2.26-4.4-.44-.06-.9-.1-1.36-.1z")
}
private val CloudVec by lazy {
    vec("cloud", "M19.35 10.04C18.67 6.59 15.64 4 12 4 9.11 4 6.6 5.64 5.35 8.04 2.34 8.36 0 10.91 0 14c0 3.31 2.69 6 6 6h13c2.76 0 5-2.24 5-5 0-2.64-2.05-4.78-4.65-4.96z")
}
private val DropVec by lazy {
    vec("drop", "M12 2c-5.33 4.55-8 8.48-8 11.8 0 4.98 3.8 8.2 8 8.2s8-3.22 8-8.2c0-3.32-2.67-7.25-8-11.8z")
}
private val BoltVec by lazy { vec("bolt", "M7 2v11h3v9l7-12h-4l4-8z") }
private val SnowVec by lazy {
    vec("snow", "M22 11h-4.17l3.24-3.24-1.41-1.42L15 11h-2V9l4.66-4.66-1.42-1.41L13 6.17V2h-2v4.17L7.76 2.93 6.34 4.34 11 9v2H9L4.34 6.34 2.93 7.76 6.17 11H2v2h4.17l-3.24 3.24 1.41 1.42L9 13h2v2l-4.66 4.66 1.42 1.41L11 17.83V22h2v-4.17l3.24 3.24 1.42-1.41L13 15v-2h2l4.66 4.66 1.41-1.42L17.83 13H22z")
}
private val FogVec by lazy { vec("fog", "M3 18h18v-2H3v2zm0-5h18v-2H3v2zm0-7v2h18V6H3z") }

private val SunColor = Color(0xFFFFC857)
private val CloudColor = Color(0xFFE3EAF2)

@Composable
fun WeatherGlyph(cond: Cond, size: Dp, modifier: Modifier = Modifier) {
    val small = size * 0.58f
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        when (cond) {
            Cond.SUN -> Icon(SunVec, null, Modifier.size(size), tint = SunColor)
            Cond.MOON -> Icon(MoonVec, null, Modifier.size(size * 0.9f), tint = Color(0xFFFFE9A8))
            Cond.CLOUD -> Icon(CloudVec, null, Modifier.size(size), tint = CloudColor)
            Cond.FOG -> Icon(FogVec, null, Modifier.size(size * 0.9f), tint = CloudColor.copy(alpha = 0.8f))
            Cond.SNOW -> Icon(SnowVec, null, Modifier.size(size * 0.9f), tint = Color(0xFFBFE6FF))
            Cond.PARTLY -> {
                Icon(SunVec, null, Modifier.size(small).align(Alignment.TopEnd), tint = SunColor)
                Icon(CloudVec, null, Modifier.size(size * 0.82f).align(Alignment.BottomStart), tint = CloudColor)
            }
            Cond.RAIN -> {
                Icon(CloudVec, null, Modifier.size(size * 0.9f).align(Alignment.TopCenter), tint = CloudColor)
                Icon(DropVec, null, Modifier.size(size * 0.3f).align(Alignment.BottomCenter).offset(x = -size * 0.2f), tint = Color(0xFF64B5F6))
                Icon(DropVec, null, Modifier.size(size * 0.3f).align(Alignment.BottomCenter).offset(x = size * 0.2f), tint = Color(0xFF64B5F6))
            }
            Cond.STORM -> {
                Icon(CloudVec, null, Modifier.size(size * 0.9f).align(Alignment.TopCenter), tint = Color(0xFFB8C2CF))
                Icon(BoltVec, null, Modifier.size(size * 0.5f).align(Alignment.BottomCenter), tint = SunColor)
            }
        }
    }
}

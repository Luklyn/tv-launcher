@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.custom.tvlauncher.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.custom.tvlauncher.data.DayForecast
import com.custom.tvlauncher.data.Weather

/**
 * Météo de la semaine, dans l'esprit de l'appli Météo des Pixel : conditions actuelles en grand,
 * puis une ligne par jour avec une barre de températures (min → max) sur l'échelle de la semaine.
 * OK ou Retour pour fermer.
 */
@Composable
fun WeatherPanel(city: String, current: Weather?, week: List<DayForecast>, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.40f)),
        contentAlignment = Alignment.TopEnd,
    ) {
        val shape = RoundedCornerShape(30.dp)
        Column(
            Modifier
                .padding(top = 76.dp, end = 48.dp)
                .width(470.dp)
                .clip(shape)
                .background(Brush.verticalGradient(listOf(Color(0xF01B2A44), Color(0xF0121A2B))))
                .border(1.dp, Color.White.copy(alpha = 0.14f), shape)
                .focusRequester(focus)
                .focusable()
                .onKeyEvent { e ->
                    if (e.type == KeyEventType.KeyUp && (e.key == Key.DirectionCenter || e.key == Key.Enter)) {
                        onClose(); true
                    } else false
                }
                .padding(horizontal = 26.dp, vertical = 22.dp),
        ) {
            // --- Conditions actuelles
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(city, color = Color.White.copy(alpha = 0.75f), fontSize = 14.sp, maxLines = 1)
                    Text(
                        if (current != null) "${current.tempC}°" else "--°",
                        color = Color.White, fontSize = 64.sp, fontWeight = FontWeight.Light,
                    )
                    if (current != null) {
                        Text(
                            current.description.replaceFirstChar { it.uppercase() },
                            color = Color.White.copy(alpha = 0.85f), fontSize = 15.sp, maxLines = 1,
                        )
                    }
                }
                if (current != null) WeatherGlyph(condOf(current.iconCode), 72.dp)
            }

            Spacer(Modifier.height(14.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.12f)))
            Spacer(Modifier.height(8.dp))

            // --- Prévisions
            if (week.isEmpty()) {
                Text("Prévisions indisponibles", color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp, modifier = Modifier.padding(vertical = 12.dp))
            } else {
                val lo = week.minOf { it.min }
                val hi = week.maxOf { it.max }
                week.forEachIndexed { i, d -> DayRow(d, lo, hi, today = i == 0) }
            }
        }
    }
}

@Composable
private fun DayRow(d: DayForecast, weekMin: Int, weekMax: Int, today: Boolean) {
    val span = (weekMax - weekMin).coerceAtLeast(1).toFloat()
    val start = ((d.min - weekMin) / span).coerceIn(0f, 1f)
    val end = ((d.max - weekMin) / span).coerceIn(start, 1f)
    Row(Modifier.fillMaxWidth().height(38.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            d.label, color = Color.White, fontSize = 15.sp,
            fontWeight = if (today) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.width(56.dp), maxLines = 1,
        )
        WeatherGlyph(condOfWmo(d.code), 26.dp, Modifier.padding(end = 14.dp))
        Text("${d.min}°", color = Color.White.copy(alpha = 0.6f), fontSize = 15.sp, modifier = Modifier.width(40.dp))
        // Barre de la semaine : la portion colorée va de la min à la max du jour
        Row(Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp)).background(Color.White.copy(alpha = 0.16f))) {
            if (start > 0.001f) Spacer(Modifier.weight(start))
            Box(
                Modifier
                    .weight((end - start).coerceAtLeast(0.06f))
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Brush.horizontalGradient(listOf(Color(0xFF64B5F6), Color(0xFFFFB74D)))),
            )
            if (1f - end > 0.001f) Spacer(Modifier.weight(1f - end))
        }
        Text(
            "${d.max}°", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium,
            modifier = Modifier.width(48.dp).padding(start = 10.dp),
        )
    }
}

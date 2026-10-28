@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.custom.tvlauncher.ui

import android.graphics.Typeface
import android.util.TypedValue
import android.widget.TextClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.custom.tvlauncher.BuildConfig
import com.custom.tvlauncher.data.SystemInfo
import com.custom.tvlauncher.data.Weather
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ClockBlock(weather: Weather?, system: SystemInfo, modifier: Modifier = Modifier) {
    val dateText = rememberDateText()
    Column(modifier) {
        AndroidView(factory = { ctx ->
            TextClock(ctx).apply {
                format24Hour = "HH:mm"
                format12Hour = "HH:mm"
                setTextColor(android.graphics.Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 54f)
                typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
                includeFontPadding = false
            }
        })
        Spacer(Modifier.height(4.dp))
        Text(dateText, color = Color.White, fontSize = 15.sp)
        Spacer(Modifier.height(10.dp))
        if (weather != null) WeatherPill(weather)
        if (BuildConfig.SHOW_SYSTEM_STATS) Spacer(Modifier.height(8.dp))
        if (BuildConfig.SHOW_SYSTEM_STATS) SystemGauges(system)
    }
}

@Composable
private fun WeatherPill(w: Weather) {
    Row(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(GlassFill)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WeatherGlyph(condOf(w.iconCode), 24.dp)
        Spacer(Modifier.width(8.dp))
        Text("${w.tempC}°C", color = Color.White, fontSize = 16.sp)
    }
}

/** Mémoire vive et stockage : deux petits anneaux discrets sous la météo. */
@Composable
private fun SystemGauges(info: SystemInfo) {
    Row(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(GlassFill)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Gauge("RAM", info.ramUsed)
        Spacer(Modifier.width(14.dp))
        Gauge("Disque", info.storageUsed)
    }
}

@Composable
private fun Gauge(label: String, used: Float) {
    // Vert → orange → rouge selon le remplissage
    val color = when {
        used > 0.9f -> Color(0xFFEF5350)
        used > 0.75f -> Color(0xFFFFB74D)
        else -> Color.White
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(20.dp)) {
            val stroke = 3.dp.toPx()
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(Color.White.copy(alpha = 0.22f), 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
            drawArc(color, -90f, 360f * used, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        Spacer(Modifier.width(6.dp))
        Column {
            Text(label, color = Color.White.copy(alpha = 0.65f), fontSize = 9.sp, maxLines = 1)
            Text("${(used * 100).toInt()} %", color = Color.White, fontSize = 12.sp, maxLines = 1)
        }
    }
}

/** Bouton rond de l'en-tête (fond d'écran, météo, paramètres). */
@Composable
fun HeaderButton(icon: ImageVector, description: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    GlassSurface(onClick = onClick, modifier = modifier.size(44.dp), shape = CircleShape) {
        Icon(icon, contentDescription = description, modifier = Modifier.align(Alignment.Center).size(24.dp))
    }
}

@Composable
fun rememberDateText(): String {
    fun fmt(): String =
        SimpleDateFormat("EEEE d MMMM", Locale.FRANCE).format(Date()).replaceFirstChar { it.uppercase() }

    var text by remember { mutableStateOf(fmt()) }
    LaunchedEffect(Unit) {
        while (true) {
            text = fmt()
            delay(30_000)
        }
    }
    return text
}

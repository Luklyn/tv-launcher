@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.custom.tvlauncher.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.darkColorScheme

@Composable
fun LauncherTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color.White,
            background = Color.Black,
            surface = Color(0xFF1B1F27),
        ),
        content = content,
    )
}

// Glassmorphism : voile blanc translucide + liseré clair. Le vrai flou d'arrière-plan n'existe pas
// en Compose ; on l'approxime avec la transparence par-dessus le fond d'écran déjà assombri.
val GlassFill = Color.White.copy(alpha = 0.10f)
val GlassFillFocused = Color.White.copy(alpha = 0.24f)
val GlassBorder = Color.White.copy(alpha = 0.16f)

/** Surface cliquable (clic + appui long) avec halo, bordure et zoom au focus D-pad. */
@Composable
fun GlassSurface(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    shape: Shape = RoundedCornerShape(16.dp),
    content: @Composable BoxScope.() -> Unit,
) {
    Surface(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier,
        shape = ClickableSurfaceDefaults.shape(shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = GlassFill,
            contentColor = Color.White,
            focusedContainerColor = GlassFillFocused,
            focusedContentColor = Color.White,
            pressedContainerColor = GlassFillFocused,
            pressedContentColor = Color.White,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.07f),
        border = ClickableSurfaceDefaults.border(
            border = Border(BorderStroke(1.dp, GlassBorder), shape = shape),
            focusedBorder = Border(BorderStroke(3.dp, Color.White), shape = shape),
        ),
        content = content,
    )
}

@Composable
fun GlassBox(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(modifier = modifier, content = content)
}

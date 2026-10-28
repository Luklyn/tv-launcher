@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.custom.tvlauncher.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.custom.tvlauncher.data.AppEntry
import com.custom.tvlauncher.data.Suggestion

val TileWidth = 160.dp
val TileHeight = 90.dp

@Composable
fun SectionTitle(text: String, subtitle: String? = null) {
    Column(Modifier.padding(start = 16.dp)) {
        Text(text, color = Color.White, fontSize = 19.sp)
        if (subtitle != null) Text(subtitle, color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp)
    }
}

/** Tuile d'application : bannière Leanback si disponible, sinon icône + nom. */
@Composable
fun AppTile(
    app: AppEntry,
    modifier: Modifier = Modifier,
    isFavorite: Boolean = false,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    GlassSurface(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier.size(TileWidth, TileHeight),
    ) {
        if (app.banner != null) {
            Image(
                bitmap = app.banner,
                contentDescription = app.label,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            IconWithLabel(app.icon, app.label)
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.BoxScope.IconWithLabel(icon: ImageBitmap, label: String) {
    Column(
        Modifier.align(Alignment.Center).padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(icon, contentDescription = null, modifier = Modifier.size(40.dp))
        Text(
            label,
            color = Color.White,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

/** Dernière tuile de la rangée : ouvre le tiroir de toutes les applications. */
@Composable
fun MoreAppsTile(modifier: Modifier = Modifier, onClick: () -> Unit) {
    GlassSurface(onClick = onClick, modifier = modifier.size(TileWidth, TileHeight)) {
        Column(
            Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(28.dp))
            Text("Plus d'applications", color = Color.White, fontSize = 13.sp, textAlign = TextAlign.Center)
        }
    }
}

/** Vignette de suggestion 16:9 : image plein cadre et titre en bas. */
@Composable
fun SuggestionCard(
    item: Suggestion,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    GlassSurface(
        onClick = onClick,
        modifier = modifier.width(232.dp).aspectRatio(16f / 9f),
    ) {
        AsyncImage(
            model = item.imageUrl,
            contentDescription = item.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(0.45f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.8f)))
        )
        Text(
            item.title,
            color = Color.White,
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.BottomStart).padding(start = 12.dp, end = 12.dp, bottom = 9.dp),
        )
    }
}

@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.custom.tvlauncher.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.foundation.lazy.grid.TvGridCells
import androidx.tv.foundation.lazy.grid.TvLazyVerticalGrid
import androidx.tv.foundation.lazy.grid.itemsIndexed
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.custom.tvlauncher.data.AppEntry

/** Tiroir plein écran : toutes les apps. Clic = lancer, appui long = ajouter aux favoris. */
@Composable
fun AppDrawer(
    apps: List<AppEntry>,
    favoritePackages: Set<String>,
    onLaunch: (AppEntry) -> Unit,
    onAddFavorite: (AppEntry) -> Unit,
    onClose: () -> Unit,
) {
    BackHandler(onBack = onClose)
    val first = remember { FocusRequester() }
    val lastItem = remember { FocusRequester() }
    val columns = 5
    val lastRow = (apps.size - 1) / columns
    LaunchedEffect(apps.isNotEmpty()) {
        if (apps.isNotEmpty()) runCatching { first.requestFocus() }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.93f))
            .padding(horizontal = 48.dp, vertical = 32.dp),
    ) {
        Column {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Toutes les applications", color = Color.White, fontSize = 24.sp, modifier = Modifier.weight(1f))
                Text("Appui long : ajouter aux favoris", color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp)
            }
            Spacer(Modifier.height(12.dp))
            TvLazyVerticalGrid(
                columns = TvGridCells.Fixed(columns),
                contentPadding = PaddingValues(start = 8.dp, top = 8.dp, end = 8.dp, bottom = 32.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                itemsIndexed(apps, key = { _, app -> app.packageName }) { index, app ->
                    val isFav = app.packageName in favoritePackages
                    Column {
                        AppTile(
                            app = app,
                            modifier = Modifier
                                .then(if (index == 0) Modifier.focusRequester(first) else Modifier)
                                .then(if (index == apps.lastIndex) Modifier.focusRequester(lastItem) else Modifier)
                                // Bords de la grille : on garde le focus au lieu de le perdre en bas / en haut
                                .onPreviewKeyEvent { e ->
                                    if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                                    when (e.key) {
                                        Key.DirectionDown -> when {
                                            index / columns == lastRow -> true
                                            index + columns > apps.lastIndex -> {
                                                runCatching { lastItem.requestFocus() }; true
                                            }
                                            else -> false
                                        }
                                        Key.DirectionUp -> index < columns
                                        else -> false
                                    }
                                },
                            isFavorite = isFav,
                            onClick = { onLaunch(app) },
                            onLongClick = { if (!isFav) onAddFavorite(app) },
                        )
                        Row(
                            Modifier.width(TileWidth).padding(top = 6.dp, start = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (isFav) {
                                Icon(Icons.Default.Star, contentDescription = "Favori", tint = Color(0xFFFFC857), modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                            }
                            Text(app.label, color = Color.White, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

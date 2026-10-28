@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.custom.tvlauncher.ui

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.ExperimentalTvMaterial3Api
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.custom.tvlauncher.BuildConfig
import com.custom.tvlauncher.data.Service
import kotlinx.coroutines.delay

@Composable
fun LauncherScreen(
    vm: LauncherViewModel,
    onLaunch: (Intent) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val ctx = LocalContext.current
    val ui by vm.ui.collectAsStateWithLifecycle()
    val favorites = ui.favorites
    val drawerOpen = ui.drawerOpen
    val weatherOpen = ui.weatherOpen
    val overlayOpen = drawerOpen || weatherOpen
    // Service affiché : légèrement différé pour éviter de clignoter quand on parcourt vite les applis
    val focusedService = ui.focusedService
    var shownService by remember { mutableStateOf<Service?>(null) }
    LaunchedEffect(focusedService) {
        if (focusedService != null && shownService != null) delay(120) else if (focusedService == null) delay(80)
        shownService = focusedService
    }

    var editingPkg by remember { mutableStateOf<String?>(null) }
    val initialFocus = remember { FocusRequester() }

    // --- Défilement vertical piloté à la main (plus aucun décalage automatique quand on est sur les apps) ---
    // Deux "pages" : accueil (offset 0) et affiches de films (offset max). On ne bouge que si le focus est sur les affiches.
    var moviesActive by remember { mutableStateOf(false) }
    var viewportH by remember { mutableIntStateOf(0) }
    var contentH by remember { mutableIntStateOf(0) }
    val maxOffset = (contentH - viewportH).coerceAtLeast(0)
    val offset by animateFloatAsState(
        targetValue = if (moviesActive) maxOffset.toFloat() else 0f,
        animationSpec = tween(350),
        label = "page",
    )
    val peekFade by animateFloatAsState(if (moviesActive) 0f else 1f, tween(350), label = "peek")
    val blurRadius by animateDpAsState(if (drawerOpen) 24.dp else if (weatherOpen) 10.dp else 0.dp, tween(250), label = "blur")

    LaunchedEffect(ui.movies.isEmpty()) { if (ui.movies.isEmpty()) moviesActive = false }

    // Sur l'écran d'accueil, "Retour" ne doit rien faire (on reste sur le launcher).
    BackHandler(enabled = !overlayOpen && editingPkg == null) { }

    // Focus initial sur la première app favorite (et au retour du tiroir).
    LaunchedEffect(favorites.isNotEmpty(), overlayOpen) {
        if (favorites.isNotEmpty() && !overlayOpen) runCatching { initialFocus.requestFocus() }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {

        // Tout l'accueil (flouté quand le tiroir d'applications est ouvert ; flou ignoré avant Android 12)
        Box(
            Modifier
                .fillMaxSize()
                .then(if (overlayOpen) Modifier.focusProperties { canFocus = false } else Modifier)
                .then(if (blurRadius > 0.dp) Modifier.blur(blurRadius) else Modifier)
        ) {
            Wallpaper(ui.wallpaperUrl)

            // Voile sombre pour la lisibilité
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.35f),
                        1f to Color.Black.copy(alpha = 0.65f),
                    )
                )
            )

            Box(Modifier.fillMaxSize().onSizeChanged { viewportH = it.height }.clipToBounds()) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .wrapContentHeight(Alignment.Top, unbounded = true)
                        .graphicsLayer { translationY = -offset }
                        .onSizeChanged { contentH = it.height }
                        .padding(horizontal = 48.dp, vertical = 24.dp),
                ) {
                    Row(Modifier.fillMaxWidth().height(if (BuildConfig.SHOW_SUGGESTIONS) 236.dp else if (BuildConfig.SHOW_SYSTEM_STATS) 200.dp else 160.dp)) {
                        ClockBlock(ui.weather, ui.system, Modifier.padding(start = 16.dp).width(194.dp))
                        Spacer(Modifier.width(20.dp))
                        Box(Modifier.weight(1f)) {
                            Crossfade(targetState = shownService, animationSpec = tween(420), label = "suggestions") { svc ->
                                val items = svc?.let { ui.suggestions[it] }.orEmpty()
                                if (svc != null && items.isNotEmpty()) {
                                    SuggestionsSection(
                                        service = svc,
                                        items = items,
                                        onOpen = { sug ->
                                            val app = ui.allApps.firstOrNull { Service.of(it) == svc }
                                            (sug.intent ?: app?.launchIntent)?.let(onLaunch)
                                        },
                                    )
                                }
                            }
                        }
                        Row(Modifier.align(Alignment.Top), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            HeaderButton(ImageIcon, "Changer le fond d'écran", onClick = vm::changeWallpaper)
                            HeaderButton(CloudIcon, "Météo de la semaine", onClick = { vm.setWeatherOpen(true) })
                            HeaderButton(Icons.Default.Settings, "Paramètres", onClick = onOpenSettings)
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    FavoritesSection(
                        favorites = favorites,
                        initialFocus = initialFocus,
                        onFocusApp = vm::onAppFocused,
                        onFocusMore = vm::onMoreTileFocused,
                        onLaunch = { onLaunch(it.launchIntent) },
                        onLongPress = { editingPkg = it.packageName },
                        onMore = { vm.setDrawer(true) },
                    )

                    if (ui.movies.isNotEmpty()) {
                        // Grand espace : au repos, seul le haut des affiches dépasse en bas de l'écran
                        Spacer(Modifier.height(56.dp))
                        MoviesSection(
                            movies = ui.movies,
                            onActiveChange = { moviesActive = it },
                            onOpen = {
                                // Lance l'app Canal installée sur la TV (pas de lien direct vers la fiche d'un film)
                                val canal = ui.allApps.firstOrNull { it.label.contains("canal", ignoreCase = true) }
                                if (canal != null) onLaunch(canal.launchIntent)
                                else Toast.makeText(ctx, "Application Canal introuvable", Toast.LENGTH_SHORT).show()
                            },
                        )
                        Spacer(Modifier.height(24.dp))
                    }
                }
            }

            // Fondu en bas de l'écran pour adoucir l'aperçu des affiches
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(110.dp)
                    .graphicsLayer { alpha = peekFade }
                    .background(Brush.verticalGradient(0f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.6f)))
            )
        }

        AnimatedVisibility(visible = drawerOpen, enter = fadeIn(tween(200)), exit = fadeOut(tween(200))) {
            AppDrawer(
                apps = ui.allApps,
                favoritePackages = favorites.map { it.packageName }.toSet(),
                onLaunch = { onLaunch(it.launchIntent) },
                onAddFavorite = { vm.addFavorite(it.packageName) },
                onClose = { vm.setDrawer(false) },
            )
        }

        AnimatedVisibility(visible = weatherOpen, enter = fadeIn(tween(200)), exit = fadeOut(tween(200))) {
            WeatherPanel(city = BuildConfig.WEATHER_CITY, current = ui.weather, week = ui.week, onClose = { vm.setWeatherOpen(false) })
        }

        editingPkg?.let { pkg ->
            val index = favorites.indexOfFirst { it.packageName == pkg }
            if (index < 0) {
                editingPkg = null
            } else {
                EditFavoriteDialog(
                    app = favorites[index],
                    index = index,
                    count = favorites.size,
                    onMoveLeft = { vm.moveFavorite(pkg, -1) },
                    onMoveRight = { vm.moveFavorite(pkg, +1) },
                    onRemove = { vm.removeFavorite(pkg); editingPkg = null },
                    onDismiss = { editingPkg = null },
                )
            }
        }
    }
}

@Composable
private fun Wallpaper(url: String?) {
    val ctx = LocalContext.current
    Crossfade(targetState = url, animationSpec = tween(1200), label = "wallpaper") { u ->
        if (u != null) {
            AsyncImage(
                model = ImageRequest.Builder(ctx).data(u).size(1920, 1080).build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(listOf(Color(0xFF1B2A3A), Color(0xFF0B0F14)))
                )
            )
        }
    }
}

@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.custom.tvlauncher.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.foundation.lazy.list.TvLazyRow
import androidx.tv.foundation.lazy.list.itemsIndexed
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.custom.tvlauncher.data.Movie

private val PanelWidth: Dp = 340.dp
private val PosterW: Dp = 110.dp
private val PosterH: Dp = 165.dp

/**
 * Rangée d'affiches en boucle, sans titre permanent.
 * - Au repos, on n'en voit que le haut, en bas de l'écran (aperçu).
 * - Dès que le focus entre dans la rangée, l'écran remonte, la rangée glisse vers la droite et un panneau
 *   apparaît à gauche avec le titre et le résumé du film survolé.
 * OK sur une affiche = ouvrir l'application Canal.
 */
@Composable
fun MoviesSection(
    movies: List<Movie>,
    onActiveChange: (Boolean) -> Unit,
    onOpen: () -> Unit,
) {
    var shown by remember { mutableStateOf(movies.first()) }
    var active by remember { mutableStateOf(false) }
    val wrap = rememberRowWrap()
    val lastIndex = movies.size - 1
    val rowStart by animateDpAsState(
        targetValue = if (active) PanelWidth + 14.dp else 0.dp,
        animationSpec = tween(300),
        label = "movies-start",
    )

    Box(Modifier.fillMaxWidth()) {
        TvLazyRow(
            modifier = Modifier
                .padding(start = rowStart)
                .onFocusChanged { s ->
                    if (s.hasFocus != active) {
                        active = s.hasFocus
                        onActiveChange(s.hasFocus)
                    }
                },
            state = wrap.state,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            pivotOffsets = RowPivot,
        ) {
            itemsIndexed(movies, key = { _, m -> m.id }) { index, movie ->
                GlassSurface(
                    onClick = onOpen,
                    modifier = Modifier
                        .size(PosterW, PosterH)
                        .rowWrapItem(wrap, index, lastIndex)
                        .onFocusChanged { if (it.hasFocus) shown = movie },
                ) {
                    AsyncImage(
                        model = movie.posterUrl,
                        contentDescription = movie.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = active,
            enter = fadeIn(tween(250)),
            exit = fadeOut(tween(150)),
            modifier = Modifier.align(Alignment.CenterStart).padding(start = 16.dp),
        ) {
            InfoPanel(shown)
        }
    }
}

/** Panneau de gauche : titre, année et résumé du film survolé (police réduite si le résumé est long). */
@Composable
private fun InfoPanel(movie: Movie) {
    val long = movie.overview.length > 330
    val year = movie.releaseDate.take(4)
    Column(
        Modifier
            .width(PanelWidth)
            .height(PosterH + 24.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.Black.copy(alpha = 0.40f))
            .padding(14.dp),
    ) {
        Text(
            "DERNIÈRES SORTIES CANAL",
            color = Color.White.copy(alpha = 0.55f),
            fontSize = 9.sp,
            letterSpacing = 1.2.sp,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            if (year.isNotBlank()) "${movie.title} ($year)" else movie.title,
            color = Color.White,
            fontSize = 17.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            movie.overview.ifBlank { "Résumé non disponible." },
            color = Color.White.copy(alpha = 0.85f),
            fontSize = if (long) 11.sp else 12.sp,
            lineHeight = if (long) 14.sp else 16.sp,
            maxLines = if (long) 7 else 6,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.weight(1f))
        Text(
            "Ce produit utilise l'API TMDB mais n'est ni approuvé ni certifié par TMDB.",
            color = Color.White.copy(alpha = 0.45f),
            fontSize = 8.sp,
        )
    }
}

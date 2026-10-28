@file:OptIn(ExperimentalTvMaterial3Api::class, ExperimentalComposeUiApi::class)

package com.custom.tvlauncher.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.dp
import androidx.tv.foundation.PivotOffsets
import androidx.tv.foundation.lazy.list.TvLazyRow
import androidx.tv.foundation.lazy.list.items
import androidx.tv.foundation.lazy.list.itemsIndexed
import androidx.tv.material3.ExperimentalTvMaterial3Api
import com.custom.tvlauncher.data.AppEntry
import com.custom.tvlauncher.data.Service
import com.custom.tvlauncher.data.Suggestion

/**
 * Position du pivot : l'élément sélectionné se cale à 2 % de la largeur depuis le bord gauche
 * (≈ la marge de la rangée) au lieu de 0, pour que son zoom et sa bordure ne soient jamais rognés
 * quand la rangée défile.
 */
val RowPivot = PivotOffsets(parentFraction = 0.02f)

// ---------------------------------------------------------------- Suggestions

@Composable
fun SuggestionsSection(
    service: Service,
    items: List<Suggestion>,
    onOpen: (Suggestion) -> Unit,
) {
    Column {
        SectionTitle("Suggestions ${service.title}", service.subtitle)
        TvLazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            pivotOffsets = RowPivot,
        ) {
            items(items, key = { it.id }) { item ->
                SuggestionCard(item = item, onClick = { onOpen(item) })
            }
        }
    }
}

// ---------------------------------------------------------------- Applications favorites

@Composable
fun FavoritesSection(
    favorites: List<AppEntry>,
    initialFocus: FocusRequester,
    onFocusApp: (AppEntry) -> Unit,
    onFocusMore: () -> Unit,
    onLaunch: (AppEntry) -> Unit,
    onLongPress: (AppEntry) -> Unit,
    onMore: () -> Unit,
) {
    Column {
        SectionTitle("Applications favorites")
        TvLazyRow(
            modifier = Modifier.focusRestorer(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            pivotOffsets = RowPivot,
        ) {
            itemsIndexed(favorites, key = { _, app -> app.packageName }) { index, app ->
                AppTile(
                    app = app,
                    modifier = Modifier
                        .then(if (index == 0) Modifier.focusRequester(initialFocus) else Modifier)
                        .onFocusChanged { if (it.hasFocus) onFocusApp(app) },
                    onClick = { onLaunch(app) },
                    onLongClick = { onLongPress(app) },
                )
            }
            item(key = "__more__") {
                MoreAppsTile(
                    modifier = Modifier
                        .onFocusChanged { if (it.hasFocus) onFocusMore() },
                    onClick = onMore,
                )
            }
        }
    }
}

package com.custom.tvlauncher.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.tv.foundation.lazy.list.TvLazyListState
import androidx.tv.foundation.lazy.list.rememberTvLazyListState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Rangée "en boucle" (utilisée par le carrousel de films) : droite sur le dernier élément = retour au premier,
 * gauche sur le premier élément = saut au dernier.
 */
@Stable
class RowWrap(
    val state: TvLazyListState,
    private val scope: CoroutineScope,
    val first: FocusRequester,
    val last: FocusRequester,
) {
    fun jump(toLast: Boolean, lastIndex: Int) {
        scope.launch {
            state.scrollToItem(if (toLast) lastIndex else 0)
            withFrameNanos { } // laisse le temps à l'élément d'être composé avant de lui donner le focus
            runCatching { (if (toLast) last else first).requestFocus() }
        }
    }
}

@Composable
fun rememberRowWrap(first: FocusRequester = remember { FocusRequester() }): RowWrap {
    val state = rememberTvLazyListState()
    val scope = rememberCoroutineScope()
    val last = remember { FocusRequester() }
    return remember(state, first) { RowWrap(state, scope, first, last) }
}

/** À appliquer à chaque élément de la rangée avec son index et l'index du dernier élément. */
fun Modifier.rowWrapItem(wrap: RowWrap, index: Int, lastIndex: Int): Modifier {
    if (lastIndex <= 0) return this
    return when (index) {
        0 -> this
            .focusRequester(wrap.first)
            .onPreviewKeyEvent { e ->
                if (e.type == KeyEventType.KeyDown && e.key == Key.DirectionLeft) {
                    wrap.jump(toLast = true, lastIndex = lastIndex); true
                } else false
            }
        lastIndex -> this
            .focusRequester(wrap.last)
            .onPreviewKeyEvent { e ->
                if (e.type == KeyEventType.KeyDown && e.key == Key.DirectionRight) {
                    wrap.jump(toLast = false, lastIndex = lastIndex); true
                } else false
            }
        else -> this
    }
}

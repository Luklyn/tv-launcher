@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.custom.tvlauncher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.custom.tvlauncher.data.AppEntry

/**
 * Menu contextuel d'un favori (appui long). Les déplacements laissent le menu ouvert pour pouvoir
 * enchaîner plusieurs pas ; "Retour" ferme.
 */
@Composable
fun EditFavoriteDialog(
    app: AppEntry,
    index: Int,
    count: Int,
    onMoveLeft: () -> Unit,
    onMoveRight: () -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .width(360.dp)
                .background(Color(0xE61B1F27), RoundedCornerShape(20.dp))
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(app.label, color = Color.White, fontSize = 20.sp)
            Text("Position ${index + 1} sur $count", color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp)
            Spacer(Modifier.height(4.dp))
            Button(
                onClick = onMoveLeft,
                enabled = index > 0,
                modifier = Modifier.fillMaxWidth().focusRequester(firstFocus),
            ) { Text("◀  Déplacer vers la gauche") }
            Button(
                onClick = onMoveRight,
                enabled = index < count - 1,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Déplacer vers la droite  ▶") }
            Button(onClick = onRemove, modifier = Modifier.fillMaxWidth()) { Text("Retirer des favoris") }
            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Fermer") }
        }
    }
}

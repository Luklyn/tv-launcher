package com.custom.tvlauncher.data

import android.content.Context
import android.content.Intent
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap

class AppRepository(private val ctx: Context) {

    /** Toutes les applications lançables (Leanback d'abord, puis apps "mobiles"), hors ce launcher. */
    fun loadAll(): List<AppEntry> {
        val pm = ctx.packageManager
        val leanback = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
        val regular = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)

        val infos = pm.queryIntentActivities(leanback, 0) + pm.queryIntentActivities(regular, 0)

        return infos
            .distinctBy { it.activityInfo.packageName }
            .filter { it.activityInfo.packageName != ctx.packageName }
            .mapNotNull { ri ->
                try {
                    val pkg = ri.activityInfo.packageName
                    val intent = Intent(Intent.ACTION_MAIN)
                        .setClassName(pkg, ri.activityInfo.name)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                    AppEntry(
                        packageName = pkg,
                        label = ri.loadLabel(pm).toString(),
                        launchIntent = intent,
                        icon = ri.loadIcon(pm).toBitmap(128, 128).asImageBitmap(),
                        banner = ri.activityInfo.loadBanner(pm)?.toBitmap(320, 180)?.asImageBitmap(),
                    )
                } catch (e: Exception) {
                    null
                }
            }
            .sortedBy { it.label.lowercase() }
    }
}

/** Persistance de l'ordre des favoris (liste de noms de paquets). */
class FavoritesStore(ctx: Context) {
    private val prefs = ctx.getSharedPreferences("favorites", Context.MODE_PRIVATE)

    /** null = jamais initialisé. */
    fun load(): List<String>? =
        prefs.getString(KEY, null)?.split(",")?.filter { it.isNotBlank() }

    fun save(packages: List<String>) {
        prefs.edit().putString(KEY, packages.joinToString(",")).apply()
    }

    companion object {
        private const val KEY = "packages"

        /** Favoris proposés au premier lancement (ceux qui sont installés). */
        val DEFAULTS = listOf(
            "com.google.android.youtube.tv",
            "com.netflix.ninja",
            "com.amazon.amazonvideo.livingroom",
            "com.plexapp.android",
            "org.videolan.vlc",
        )
    }
}

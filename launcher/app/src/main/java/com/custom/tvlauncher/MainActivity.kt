package com.custom.tvlauncher

import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import com.custom.tvlauncher.ui.LauncherScreen
import com.custom.tvlauncher.ui.LauncherTheme
import com.custom.tvlauncher.ui.LauncherViewModel

class MainActivity : ComponentActivity() {

    private val vm: LauncherViewModel by viewModels()

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = vm.reloadApps()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LauncherTheme {
                LauncherScreen(
                    vm = vm,
                    onLaunch = ::launch,
                    onOpenSettings = ::openSettings,
                )
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(this, packageReceiver, filter, ContextCompat.RECEIVER_EXPORTED)
    }

    /** Appui sur Home alors que le launcher est déjà au premier plan. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        vm.setDrawer(false)
        vm.setWeatherOpen(false)
    }

    /**
     * Intercepte la touche "réglages" (roue crantée) de la télécommande pour ouvrir
     * les paramètres natifs d'Android TV. Le log permet de trouver le keycode exact
     * de ta télécommande Philips si elle n'envoie pas KEYCODE_SETTINGS (176).
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
            Log.d(TAG, "keyCode=${event.keyCode} (${KeyEvent.keyCodeToString(event.keyCode)})")
        }
        if (event.keyCode in SETTINGS_KEYS) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) openSettings()
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    private fun openSettings() {
        try {
            startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, "Réglages indisponibles", Toast.LENGTH_SHORT).show()
        }
    }

    private fun launch(intent: Intent) {
        try {
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Impossible d'ouvrir cette application", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(packageReceiver) }
        super.onDestroy()
    }

    private companion object {
        const val TAG = "TvLauncher"
        // Ajoute ici le keycode relevé dans Logcat si ta télécommande en utilise un autre.
        val SETTINGS_KEYS = setOf(KeyEvent.KEYCODE_SETTINGS)
    }
}

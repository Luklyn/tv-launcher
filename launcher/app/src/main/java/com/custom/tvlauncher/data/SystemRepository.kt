package com.custom.tvlauncher.data

import android.app.ActivityManager
import android.content.Context
import android.os.Environment
import android.os.StatFs

/** Lit l'utilisation de la mémoire vive et du stockage (lecture seule, aucune autorisation requise). */
class SystemRepository(private val ctx: Context) {

    fun read(): SystemInfo {
        val mi = ActivityManager.MemoryInfo()
        ctx.getSystemService(ActivityManager::class.java)?.getMemoryInfo(mi)
        val sf = StatFs(Environment.getDataDirectory().path)
        val gb = 1024.0 * 1024.0 * 1024.0
        return SystemInfo(
            ramFreeMb = mi.availMem / (1024 * 1024),
            ramTotalMb = (mi.totalMem / (1024 * 1024)).coerceAtLeast(1),
            storageFreeGb = sf.availableBytes / gb,
            storageTotalGb = (sf.totalBytes / gb).coerceAtLeast(0.1),
        )
    }
}

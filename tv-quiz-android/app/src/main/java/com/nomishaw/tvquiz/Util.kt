package com.nomishaw.tvquiz

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import android.provider.Settings
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Collections

object Apps {
    data class App(val pkg: String, val label: String)

    // Apps pre-selected as "video" the first time the app runs. Parents can change the list.
    private val VIDEO = Regex(
        "youtube|netflix|disney|amazon\\.avod|amazon\\.video|primevideo|tiktok|musically|aweme|" +
            "kuaishou|bili|iqiyi|viu|wetv|qqlive|mewatch|mediacorp|hbo|hotstar|twitch|vimeo|" +
            "crunchyroll|youku|mgtv|plex|kodi|tubi|pluto",
        RegexOption.IGNORE_CASE
    )

    fun installed(ctx: Context): List<App> {
        val pm = ctx.packageManager
        val seen = LinkedHashMap<String, App>()
        for (cat in listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)) {
            val found = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(cat), 0)
            for (r in found) {
                val p = r.activityInfo.packageName
                if (p == ctx.packageName || seen.containsKey(p)) continue
                seen[p] = App(p, r.loadLabel(pm).toString())
            }
        }
        return seen.values.sortedBy { it.label.lowercase() }
    }

    fun ensureDefaults(ctx: Context) {
        synchronized(Store) {
            if (Store.appsInitialized) return
            installed(ctx)
                .filter { VIDEO.containsMatchIn(it.pkg) || VIDEO.containsMatchIn(it.label) }
                .forEach { Store.apps.add(it.pkg) }
            Store.appsInitialized = true
            Store.save()
        }
    }
}

object Perms {
    fun hasUsage(ctx: Context): Boolean {
        val ao = ctx.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= 29) {
            ao.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName)
        } else {
            @Suppress("DEPRECATION")
            ao.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun hasOverlay(ctx: Context): Boolean = Settings.canDrawOverlays(ctx)
}

object Net {
    fun lanIp(): String? = try {
        Collections.list(NetworkInterface.getNetworkInterfaces() ?: return null)
            .filter { it.isUp && !it.isLoopback }
            .flatMap { Collections.list(it.inetAddresses) }
            .firstOrNull { it is Inet4Address && it.isSiteLocalAddress }
            ?.hostAddress
    } catch (e: Exception) {
        null
    }
}

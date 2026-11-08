package com.nomishaw.tvquiz

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import kotlin.concurrent.thread

/**
 * The once-a-second loop: work out which app is on screen, update the rules,
 * and bring up the quiz / lock screen when needed.
 *
 * Started by whichever of [WatchService] or [QuizAccessibilityService] runs
 * first; on TVs where adb isn't available the accessibility service alone is
 * enough (it reports the foreground app and is allowed to open activities).
 */
object Engine {
    private const val TAG = "TvQuiz"
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var app: Context
    private var running = false
    private var lastQuery = 0L
    private var usageForeground: String? = null
    private var lastLaunch = 0L

    /** Set by the accessibility service on every window change. */
    @Volatile var a11yForeground: String? = null

    fun start(ctx: Context) {
        if (running) return
        running = true
        app = ctx.applicationContext
        Store.init(app)
        thread(name = "tvquiz-init") {
            Apps.ensureDefaults(app)
            try { RemoteServer.ensureStarted(app) } catch (e: Exception) { Log.w(TAG, "server", e) }
        }
        handler.post(loop)
    }

    private val loop = object : Runnable {
        override fun run() {
            try { step() } catch (e: Exception) { Log.w(TAG, "tick", e) }
            handler.postDelayed(this, 1000)
        }
    }

    private fun step() {
        val pm = app.getSystemService(Context.POWER_SERVICE) as PowerManager
        val fg = currentForeground()
        val need = Store.tick(fg, app.packageName, pm.isInteractive)
        val now = System.currentTimeMillis()
        if (need && fg != app.packageName && now - lastLaunch > 1500) {
            lastLaunch = now
            showQuiz()
        }
    }

    private fun showQuiz() {
        // Prefer the accessibility service as the launcher: Android lets it open
        // activities from the background without the overlay permission.
        val launcher: Context = QuizAccessibilityService.instance ?: app
        try {
            launcher.startActivity(
                Intent(launcher, QuizActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            )
        } catch (e: Exception) {
            Log.w(TAG, "launch", e)
        }
    }

    private fun currentForeground(): String? {
        if (QuizAccessibilityService.instance != null) return a11yForeground
        if (!Perms.hasUsage(app)) return null
        return usageStatsForeground()
    }

    @Suppress("DEPRECATION")
    private fun usageStatsForeground(): String? {
        val usm = app.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val end = System.currentTimeMillis()
        val begin = if (lastQuery == 0L) end - 10 * 60_000 else lastQuery - 2000
        val events = usm.queryEvents(begin, end) ?: return usageForeground
        val e = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            if (e.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) usageForeground = e.packageName
        }
        lastQuery = end
        return usageForeground
    }
}

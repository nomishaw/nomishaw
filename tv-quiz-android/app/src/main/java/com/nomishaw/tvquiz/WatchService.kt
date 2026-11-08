package com.nomishaw.tvquiz

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import kotlin.concurrent.thread

/**
 * Runs in the background, checks once a second which app is on screen, counts
 * watching time and brings up the quiz / lock screen when the rules say so.
 */
class WatchService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var lastQuery = 0L
    private var foreground: String? = null
    private var lastLaunch = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Store.init(this)
        startAsForeground()
        thread(name = "tvquiz-init") {
            Apps.ensureDefaults(this)
            try { RemoteServer.ensureStarted(this) } catch (e: Exception) { Log.w(TAG, "server", e) }
        }
        handler.post(loop)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        handler.removeCallbacks(loop)
        super.onDestroy()
    }

    private val loop = object : Runnable {
        override fun run() {
            try { step() } catch (e: Exception) { Log.w(TAG, "tick", e) }
            handler.postDelayed(this, 1000)
        }
    }

    private fun step() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        val fg = currentForeground()
        val need = Store.tick(fg, packageName, pm.isInteractive)
        val now = System.currentTimeMillis()
        if (need && fg != packageName && now - lastLaunch > 1500) {
            lastLaunch = now
            startActivity(
                Intent(this, QuizActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            )
        }
    }

    @Suppress("DEPRECATION")
    private fun currentForeground(): String? {
        val usm = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val end = System.currentTimeMillis()
        val begin = if (lastQuery == 0L) end - 10 * 60_000 else lastQuery - 2000
        val events = usm.queryEvents(begin, end) ?: return foreground
        val e = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            if (e.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) foreground = e.packageName
        }
        lastQuery = end
        return foreground
    }

    private fun startAsForeground() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val builder = if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "答题电视", NotificationManager.IMPORTANCE_MIN)
            )
            Notification.Builder(this, CHANNEL)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        val n = builder
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setContentTitle("答题电视正在守护")
            .setContentText("每看一段时间要答一道题")
            .setOngoing(true)
            .build()
        startForeground(1, n)
    }

    companion object {
        private const val TAG = "TvQuiz"
        private const val CHANNEL = "watch"

        fun start(ctx: Context) {
            val i = Intent(ctx, WatchService::class.java)
            if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i) else ctx.startService(i)
        }
    }
}

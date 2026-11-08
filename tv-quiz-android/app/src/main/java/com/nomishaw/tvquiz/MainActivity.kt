package com.nomishaw.tvquiz

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.widget.Toast
import kotlin.concurrent.thread

/** Parent panel on the TV itself. Same page the parent's phone opens over Wi-Fi. */
class MainActivity : Activity() {
    private lateinit var web: WebView
    private var token = ""

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Store.init(this)
        WatchService.start(this)
        token = intent.getStringExtra("token") ?: ""
        web = WebView(this)
        web.setBackgroundColor(Color.rgb(13, 23, 38))
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.addJavascriptInterface(Native(), "Native")
        setContentView(web)
        thread(name = "tvquiz-start") {
            val ok = try { RemoteServer.ensureStarted(this); true } catch (e: Exception) { false }
            runOnUiThread {
                if (ok) web.loadUrl("http://127.0.0.1:${RemoteServer.PORT}/")
                else web.loadData("<h2 style='color:#fff'>端口 ${RemoteServer.PORT} 被占用，请重启电视后再试</h2>", "text/html; charset=utf-8", null)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra("token")?.let { token = it; web.reload() }
    }

    override fun onDestroy() {
        web.destroy()
        super.onDestroy()
    }

    inner class Native {
        /** One-time login handed over after the PIN was typed on the quiz/lock screen. */
        @JavascriptInterface
        fun token(): String = token.also { token = "" }

        @JavascriptInterface
        fun openSettings(kind: String) {
            runOnUiThread {
                val intent = when (kind) {
                    "a11y" -> Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    "usage" -> Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                    "overlay" -> Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
                    else -> Intent(Settings.ACTION_SETTINGS)
                }
                try {
                    startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(this@MainActivity, "这台电视没有这个设置页，请按说明用 adb 命令开启", Toast.LENGTH_LONG).show()
                }
            }
        }

        @JavascriptInterface
        fun close() {
            runOnUiThread { finish() }
        }
    }
}

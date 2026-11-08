package com.nomishaw.tvquiz

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.WebView

/** Full-screen quiz / lock screen shown on top of the video app. */
class QuizActivity : Activity() {
    private lateinit var web: WebView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Store.init(this)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        web = WebView(this)
        web.setBackgroundColor(Color.rgb(11, 20, 34))
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.addJavascriptInterface(Bridge(), "TV")
        setContentView(web)
        web.loadUrl("file:///android_asset/quiz.html")
        web.requestFocus()
    }

    // The Back button does nothing here; the child has to answer.
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {}

    override fun onDestroy() {
        web.destroy()
        super.onDestroy()
    }

    inner class Bridge {
        @JavascriptInterface
        fun state(): String = Store.quizState().toString()

        @JavascriptInterface
        fun answer(correct: Boolean, q: String, chosen: String, ans: String): String {
            Store.onAnswer(correct, q, chosen, ans)
            return state()
        }

        @JavascriptInterface
        fun done() {
            runOnUiThread { if (Store.mode() == "none") finish() }
        }

        @JavascriptInterface
        fun checkPin(pin: String): Boolean {
            if (!RemoteServer.tryPin(pin)) return false
            val token = RemoteServer.newToken()
            runOnUiThread {
                startActivity(Intent(this@QuizActivity, MainActivity::class.java).putExtra("token", token))
                finish()
            }
            return true
        }
    }
}

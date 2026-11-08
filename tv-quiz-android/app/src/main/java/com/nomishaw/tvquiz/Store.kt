package com.nomishaw.tvquiz

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * All rules and state live here, persisted to SharedPreferences so a reboot or a
 * force-stop doesn't reset the countdown, owed questions or the penalty wait.
 */
object Store {
    private lateinit var prefs: SharedPreferences

    // Parent settings
    var pin = "0000"
    var interval = 10       // minutes of watching between questions
    var penalty = 58        // seconds to wait after a wrong answer
    var extra = 1           // extra questions added per wrong answer
    var maxPending = 3      // cap on owed questions
    var daily = 0           // daily watching limit in minutes, 0 = none
    var bank = "math7"
    var custom = ""
    val apps = linkedSetOf<String>()   // packages that count as "watching"
    var appsInitialized = false

    // Live state
    var watched = 0         // seconds watched since the last question
    var pending = 0         // questions still owed
    var cooldownUntil = 0L
    var unlockUntil = 0L
    var locked = false

    // Today's stats
    var day = ""
    var statWatch = 0       // seconds
    var statRight = 0
    var statWrong = 0
    private val log = ArrayList<JSONObject>()

    @Synchronized
    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.applicationContext.getSharedPreferences("tvquiz", Context.MODE_PRIVATE)
        load()
        rollDay()
    }

    private fun now() = System.currentTimeMillis()
    private fun today(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    private fun load() {
        val raw = prefs.getString("state", null) ?: return
        try {
            val o = JSONObject(raw)
            pin = o.optString("pin", pin)
            interval = o.optInt("interval", interval)
            penalty = o.optInt("penalty", penalty)
            extra = o.optInt("extra", extra)
            maxPending = o.optInt("maxPending", maxPending)
            daily = o.optInt("daily", daily)
            bank = o.optString("bank", bank)
            custom = o.optString("custom", custom)
            appsInitialized = o.optBoolean("appsInitialized", false)
            o.optJSONArray("apps")?.let { a ->
                apps.clear()
                for (i in 0 until a.length()) apps.add(a.getString(i))
            }
            watched = o.optInt("watched", 0)
            pending = o.optInt("pending", 0)
            cooldownUntil = o.optLong("cooldownUntil", 0)
            unlockUntil = o.optLong("unlockUntil", 0)
            locked = o.optBoolean("locked", false)
            day = o.optString("day", "")
            statWatch = o.optInt("statWatch", 0)
            statRight = o.optInt("statRight", 0)
            statWrong = o.optInt("statWrong", 0)
            o.optJSONArray("log")?.let { a ->
                log.clear()
                for (i in 0 until a.length()) log.add(a.getJSONObject(i))
            }
        } catch (e: Exception) {
            // Corrupt state: keep defaults.
        }
    }

    @Synchronized
    fun save() {
        val o = JSONObject()
            .put("pin", pin).put("interval", interval).put("penalty", penalty)
            .put("extra", extra).put("maxPending", maxPending).put("daily", daily)
            .put("bank", bank).put("custom", custom)
            .put("appsInitialized", appsInitialized).put("apps", JSONArray(apps.toList()))
            .put("watched", watched).put("pending", pending)
            .put("cooldownUntil", cooldownUntil).put("unlockUntil", unlockUntil)
            .put("locked", locked).put("day", day)
            .put("statWatch", statWatch).put("statRight", statRight).put("statWrong", statWrong)
            .put("log", JSONArray(log))
        prefs.edit().putString("state", o.toString()).apply()
    }

    @Synchronized
    private fun rollDay() {
        val t = today()
        if (day != t) {
            day = t
            statWatch = 0; statRight = 0; statWrong = 0
            log.clear()
            save()
        }
    }

    fun unlocked() = unlockUntil > now()
    fun dailyCapHit() = daily > 0 && statWatch >= daily * 60

    @Synchronized
    fun mode(): String = when {
        locked || dailyCapHit() -> "lock"
        unlocked() -> "none"
        pending > 0 -> "quiz"
        else -> "none"
    }

    @Synchronized
    fun statusText(): String = when {
        locked -> "已锁屏"
        dailyCapHit() -> "今日观看已达上限（$daily 分钟）"
        unlocked() -> "大人解锁中，还剩约 ${((unlockUntil - now() + 59_999) / 60_000)} 分钟"
        pending > 0 -> "答题中，还需答对 $pending 道"
        else -> {
            val left = maxOf(0, interval * 60 - watched)
            "答题模式：再看 %d:%02d 出题".format(left / 60, left % 60)
        }
    }

    private fun statusClass(): String = when {
        locked || dailyCapHit() -> "off"
        pending > 0 -> "warn"
        else -> "on"
    }

    private fun addLog(text: String, cls: String = "") {
        val t = SimpleDateFormat("HH:mm", Locale.US).format(Date())
        log.add(0, JSONObject().put("t", t).put("text", text).put("cls", cls))
        while (log.size > 80) log.removeAt(log.size - 1)
    }

    /**
     * Called once a second by [WatchService]. Returns true when the quiz / lock
     * screen must be brought to the front.
     */
    @Synchronized
    fun tick(fg: String?, own: String, screenOn: Boolean): Boolean {
        rollDay()
        if (unlockUntil != 0L && !unlocked()) {
            unlockUntil = 0; watched = 0
            addLog("大人解锁结束，恢复答题")
            save()
        }
        if (!screenOn || fg == null || fg == own) return false

        val isWatched = fg in apps
        var need = false
        if (isWatched) statWatch++
        if (locked) {
            need = true
        } else if (isWatched) {
            if (dailyCapHit()) {
                need = true
            } else if (!unlocked()) {
                if (pending > 0) {
                    need = true
                } else {
                    watched++
                    if (watched >= interval * 60) {
                        pending = 1; watched = 0
                        addLog("看满 $interval 分钟，出题")
                        need = true
                    }
                }
            }
        }
        if (isWatched || need) save()
        return need
    }

    @Synchronized
    fun quizState(): JSONObject = JSONObject()
        .put("mode", mode())
        .put("pending", pending)
        .put("cooldownLeft", maxOf(0L, cooldownUntil - now()))
        .put("lockReason", if (locked) "家长已锁屏，去做点别的事吧！" else "今天已经看满 $daily 分钟啦，明天再看！")
        .put("bank", bank)
        .put("custom", custom)

    @Synchronized
    fun onAnswer(correct: Boolean, q: String, chosen: String, answer: String) {
        if (mode() != "quiz") return
        if (correct) {
            pending = maxOf(0, pending - 1)
            statRight++
            addLog("✔ $q", "o")
        } else {
            pending = minOf(maxPending, pending + extra).coerceAtLeast(1)
            statWrong++
            cooldownUntil = now() + penalty * 1000L
            addLog("✘ $q（选了 $chosen，正确 $answer）", "x")
        }
        if (pending == 0) watched = 0
        save()
    }

    @Synchronized
    fun action(name: String): Boolean {
        when (name) {
            "unlock" -> {
                locked = false; unlockUntil = now() + 2 * 3600_000L
                pending = 0; cooldownUntil = 0
                addLog("大人解锁 2 小时")
            }
            "lock" -> {
                locked = true; unlockUntil = 0
                addLog("家长立即锁屏", "x")
            }
            "cancel" -> {
                locked = false; unlockUntil = 0; watched = 0
                addLog("取消解锁，恢复答题模式")
            }
            "quiz" -> {
                locked = false; unlockUntil = 0; watched = 0
                if (pending == 0) pending = 1
                addLog("家长要求马上答题")
            }
            else -> return false
        }
        save()
        return true
    }

    @Synchronized
    fun checkPin(p: String) = p == pin

    @Synchronized
    fun setPin(p: String): Boolean {
        if (!Regex("^\\d{4,8}$").matches(p)) return false
        pin = p
        addLog("家长密码已修改")
        save()
        return true
    }

    @Synchronized
    fun applyConfig(o: JSONObject) {
        fun num(key: String, lo: Int, hi: Int, cur: Int) =
            if (o.has(key)) o.optInt(key, cur).coerceIn(lo, hi) else cur
        interval = num("interval", 1, 120, interval)
        penalty = num("penalty", 0, 600, penalty)
        extra = num("extra", 0, 5, extra)
        maxPending = num("maxPending", 1, 10, maxPending)
        daily = num("daily", 0, 600, daily)
        if (o.has("bank")) bank = o.optString("bank", bank)
        if (o.has("custom")) custom = o.optString("custom", custom).take(20_000)
        o.optJSONArray("apps")?.let { a ->
            apps.clear()
            for (i in 0 until a.length()) apps.add(a.getString(i))
            appsInitialized = true
        }
        pending = minOf(pending, maxPending)
        save()
    }

    @Synchronized
    fun fullState(ctx: Context): JSONObject {
        val selected = apps
        val list = JSONArray()
        for (app in Apps.installed(ctx)) {
            list.put(JSONObject().put("pkg", app.pkg).put("label", app.label).put("on", app.pkg in selected))
        }
        val ip = Net.lanIp()
        return JSONObject()
            .put("status", statusText())
            .put("statusClass", statusClass())
            .put("defaultPin", pin == "0000")
            .put("cfg", JSONObject()
                .put("interval", interval).put("penalty", penalty).put("extra", extra)
                .put("maxPending", maxPending).put("daily", daily)
                .put("bank", bank).put("custom", custom))
            .put("stats", JSONObject()
                .put("watch", statWatch / 60).put("right", statRight).put("wrong", statWrong)
                .put("log", JSONArray(log)))
            .put("apps", list)
            .put("perms", JSONObject()
                .put("usage", Perms.hasUsage(ctx))
                .put("overlay", Perms.hasOverlay(ctx))
                .put("a11y", Perms.hasAccessibility(ctx)))
            .put("url", if (ip != null) "http://$ip:${RemoteServer.PORT}" else "")
    }
}

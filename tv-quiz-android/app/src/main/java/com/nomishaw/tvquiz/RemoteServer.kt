package com.nomishaw.tvquiz

import android.content.Context
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.Collections
import java.util.UUID
import kotlin.concurrent.thread

/**
 * A tiny HTTP server on the home Wi-Fi, so a parent can open the control panel
 * from a phone browser (http://<tv-ip>:8686). Every API call needs the PIN.
 */
object RemoteServer {
    const val PORT = 8686

    @Volatile private var server: ServerSocket? = null
    private lateinit var app: Context
    private val tokens = Collections.synchronizedSet(HashSet<String>())
    private var fails = 0
    private var failUntil = 0L

    @Synchronized
    fun ensureStarted(ctx: Context) {
        if (server != null) return
        app = ctx.applicationContext
        val s = ServerSocket()
        s.reuseAddress = true
        s.bind(InetSocketAddress(PORT))
        server = s
        thread(isDaemon = true, name = "tvquiz-http") {
            while (true) {
                val sock = try { s.accept() } catch (e: Exception) { break }
                thread(isDaemon = true) { handle(sock) }
            }
        }
    }

    fun newToken(): String = UUID.randomUUID().toString().also { tokens.add(it) }

    /** PIN check with a lockout after 5 wrong tries (shared by TV and phone). */
    @Synchronized
    fun tryPin(pin: String): Boolean {
        val now = System.currentTimeMillis()
        if (failUntil > now) return false
        if (Store.checkPin(pin)) { fails = 0; return true }
        if (++fails >= 5) { fails = 0; failUntil = now + 60_000 }
        return false
    }

    private fun lockedOut() = failUntil > System.currentTimeMillis()

    private class Resp(val code: Int, val type: String, val body: ByteArray)

    private fun json(code: Int, o: JSONObject) =
        Resp(code, "application/json; charset=utf-8", o.toString().toByteArray())

    private fun err(code: Int, msg: String) = json(code, JSONObject().put("error", msg))

    private fun handle(socket: Socket) {
        socket.use { sock ->
            sock.soTimeout = 10_000
            val input = BufferedInputStream(sock.getInputStream())
            val out = sock.getOutputStream()
            val requestLine = readLine(input) ?: return
            val parts = requestLine.split(" ")
            if (parts.size < 2) return
            val method = parts[0]
            val path = parts[1].substringBefore('?')
            val headers = HashMap<String, String>()
            while (true) {
                val line = readLine(input) ?: break
                if (line.isEmpty()) break
                val i = line.indexOf(':')
                if (i > 0) headers[line.substring(0, i).trim().lowercase()] = line.substring(i + 1).trim()
            }
            val len = (headers["content-length"]?.toIntOrNull() ?: 0).coerceIn(0, 100_000)
            val body = ByteArray(len)
            var read = 0
            while (read < len) {
                val n = input.read(body, read, len - read)
                if (n < 0) break
                read += n
            }
            val resp = try {
                route(method, path, headers, String(body, 0, read, Charsets.UTF_8))
            } catch (e: Exception) {
                err(500, e.message ?: "error")
            }
            write(out, resp)
        }
    }

    private fun route(method: String, path: String, headers: Map<String, String>, body: String): Resp {
        if (method == "GET" && (path == "/" || path == "/index.html")) {
            val html = app.assets.open("parent.html").use { it.readBytes() }
            return Resp(200, "text/html; charset=utf-8", html)
        }
        if (!path.startsWith("/api/")) return err(404, "not found")
        val req = if (body.isBlank()) JSONObject() else JSONObject(body)

        if (method == "POST" && path == "/api/login") {
            if (lockedOut()) return err(429, "错误次数太多，请 1 分钟后再试")
            return if (tryPin(req.optString("pin"))) json(200, JSONObject().put("token", newToken()))
            else err(403, if (lockedOut()) "错误次数太多，请 1 分钟后再试" else "密码不对")
        }

        val token = headers["x-token"] ?: ""
        if (token.isEmpty() || token !in tokens) return err(401, "请先登录")

        return when ("$method $path") {
            "GET /api/state" -> json(200, Store.fullState(app))
            "POST /api/action" ->
                if (Store.action(req.optString("action"))) json(200, Store.fullState(app))
                else err(400, "未知操作")
            "POST /api/config" -> { Store.applyConfig(req); json(200, Store.fullState(app)) }
            "POST /api/pin" ->
                if (Store.setPin(req.optString("pin"))) {
                    tokens.clear(); tokens.add(token)   // sign out every other device
                    json(200, JSONObject().put("ok", true))
                } else err(400, "密码需为 4–8 位数字")
            "POST /api/logout" -> { tokens.remove(token); json(200, JSONObject().put("ok", true)) }
            else -> err(404, "not found")
        }
    }

    private fun readLine(input: InputStream): String? {
        val buf = ByteArrayOutputStream()
        while (true) {
            val b = input.read()
            if (b < 0) return if (buf.size() == 0) null else buf.toString("UTF-8")
            if (b == '\n'.code) break
            if (b != '\r'.code) buf.write(b)
            if (buf.size() > 8192) return null
        }
        return buf.toString("UTF-8")
    }

    private fun write(out: OutputStream, r: Resp) {
        val reason = when (r.code) { 200 -> "OK"; 401 -> "Unauthorized"; 403 -> "Forbidden"; 404 -> "Not Found"; 429 -> "Too Many Requests"; else -> "Error" }
        val head = "HTTP/1.1 ${r.code} $reason\r\n" +
            "Content-Type: ${r.type}\r\n" +
            "Content-Length: ${r.body.size}\r\n" +
            "Cache-Control: no-store\r\n" +
            "Connection: close\r\n\r\n"
        out.write(head.toByteArray())
        out.write(r.body)
        out.flush()
    }
}

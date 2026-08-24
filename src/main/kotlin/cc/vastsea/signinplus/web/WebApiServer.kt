package cc.vastsea.signinplus.web

import cc.vastsea.signinplus.SignInPlus
import cc.vastsea.signinplus.storage.Checkins
import cc.vastsea.signinplus.storage.PlayerStat
import cc.vastsea.signinplus.storage.PlayerProfiles
import cc.vastsea.signinplus.storage.Points
import com.google.gson.Gson
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class WebApiServer(
    private val plugin: SignInPlus,
    private val address: String,
    private val port: Int,
    basePath: String,
    private val apiKey: String,
    private val requestsPerMinute: Int,
) {
    private val basePath = normalizeBasePath(basePath)
    private val gson = Gson()
    private val rateLimiter = FixedWindowRateLimiter(requestsPerMinute)
    private val threadNumber = AtomicInteger()
    private val executor = ThreadPoolExecutor(
        2,
        4,
        30L,
        TimeUnit.SECONDS,
        ArrayBlockingQueue(100),
        { task -> Thread(task, "SignInPlus-Web-${threadNumber.incrementAndGet()}").apply { isDaemon = true } },
        ThreadPoolExecutor.AbortPolicy()
    )
    private var server: HttpServer? = null

    fun start() {
        val addr = InetSocketAddress(address, port)
        server = HttpServer.create(addr, 50)
        val s = server ?: return
        s.createContext(path("/ifsignin"), handler { q ->
            val playerUuid = q.requiredPlayerUuid() ?: return@handler
            okText(q, Checkins.isSignedIn(playerUuid).toString())
        })
        s.createContext(path("/total"), handler { q ->
            val playerUuid = q.requiredPlayerUuid() ?: return@handler
            okText(q, Checkins.getTotalDays(playerUuid).toString())
        })
        s.createContext(path("/streak"), handler { q ->
            val playerUuid = q.requiredPlayerUuid() ?: return@handler
            okText(q, Checkins.getStreakDays(playerUuid).toString())
        })
        s.createContext(path("/last_check_in_time"), handler { q ->
            val playerUuid = q.requiredPlayerUuid() ?: return@handler
            okText(q, Checkins.getLastCheckInTime(playerUuid))
        })
        s.createContext(path("/ranktoday"), handler { q ->
            val playerUuid = q.requiredPlayerUuid() ?: return@handler
            okText(q, Checkins.getRankToday(playerUuid))
        })
        s.createContext(path("/points"), handler { q ->
            val playerUuid = q.requiredPlayerUuid() ?: return@handler
            okText(q, formatPoints(Points.getPoints(playerUuid)))
        })
        s.createContext(path("/info"), handler { q ->
            val playerUuid = q.requiredPlayerUuid() ?: return@handler
            val stat = PlayerStat.load(playerUuid)
            okJson(
                q,
                gson.toJson(
                    mapOf(
                        "uuid" to playerUuid.toString(),
                        "total" to stat.totalDays,
                        "streak" to stat.streakDays,
                        "last_check_in_time" to stat.lastCheckInTime,
                        "rank_today" to stat.rankToday,
                        "points" to stat.points / 100.0
                    )
                )
            )
        })
        s.createContext(path("/amounttoday"), handler { q ->
            okText(q, Checkins.getAmountToday().toString())
        })

        s.executor = executor
        s.start()
    }

    fun stop() {
        server?.stop(1)
        server = null
        executor.shutdown()
        try {
            if (!executor.awaitTermination(2, TimeUnit.SECONDS)) executor.shutdownNow()
        } catch (_: InterruptedException) {
            executor.shutdownNow()
            Thread.currentThread().interrupt()
        }
    }

    private fun path(sub: String): String = basePath + sub

    private fun handler(block: (Query) -> Unit): HttpHandler = HttpHandler { ex ->
        try {
            if (!isAuthorized(ex)) {
                ex.responseHeaders.add("WWW-Authenticate", "Bearer")
                respond(ex, 401, "unauthorized", "text/plain; charset=utf-8")
                return@HttpHandler
            }
            val client = ex.remoteAddress.address?.hostAddress ?: ex.remoteAddress.hostString
            if (!rateLimiter.allow(client)) {
                respond(ex, 429, "rate limit exceeded", "text/plain; charset=utf-8")
                return@HttpHandler
            }
            if (!ex.requestMethod.equals("GET", ignoreCase = true)) {
                methodNotAllowed(ex)
                return@HttpHandler
            }
            if ((ex.requestURI.rawQuery?.length ?: 0) > 2_048) {
                respond(ex, 414, "query too long", "text/plain; charset=utf-8")
                return@HttpHandler
            }
            block(Query(ex))
        } catch (t: Throwable) {
            plugin.logger.warning("Web API request failed for ${ex.requestURI.path}: ${t.message}")
            if (ex.responseCode == -1) internalError(ex)
        }
    }

    private fun isAuthorized(exchange: HttpExchange): Boolean {
        val supplied = exchange.requestHeaders.getFirst("Authorization").orEmpty()
        val expected = "Bearer $apiKey"
        return MessageDigest.isEqual(
            supplied.toByteArray(StandardCharsets.UTF_8),
            expected.toByteArray(StandardCharsets.UTF_8)
        )
    }

    private inner class Query(private val ex: HttpExchange) {
        fun uri(): URI = ex.requestURI

        fun param(key: String): String? {
            val raw = uri().rawQuery ?: return null
            return raw.split("&").mapNotNull {
                val i = it.indexOf('=')
                if (i > 0) decode(it.substring(0, i)) to decode(it.substring(i + 1)) else null
            }.toMap()[key]
        }

        fun requiredPlayerUuid(): UUID? {
            val value = param("player")?.trim()
            if (value.isNullOrEmpty()) {
                badRequest(this, "missing player")
                return null
            }
            runCatching { UUID.fromString(value) }.getOrNull()?.let { return it }
            if (value.length !in 1..16 || !value.matches(Regex("[A-Za-z0-9_]+"))) {
                badRequest(this, "invalid player")
                return null
            }
            val resolved = PlayerProfiles.resolveUuid(value)
            if (resolved == null) badRequest(this, "unknown player")
            return resolved
        }

        fun exchange(): HttpExchange = ex

        private fun decode(value: String): String =
            URLDecoder.decode(value, StandardCharsets.UTF_8)
    }

    private fun formatPoints(raw: Long): String = java.lang.String.format(java.util.Locale.ROOT, "%.2f", raw / 100.0)
    private fun okText(q: Query, text: String) = respond(q.exchange(), 200, text, "text/plain; charset=utf-8")
    private fun okJson(q: Query, text: String) = respond(q.exchange(), 200, text, "application/json; charset=utf-8")
    private fun badRequest(q: Query, text: String) = respond(q.exchange(), 400, text, "text/plain; charset=utf-8")
    private fun methodNotAllowed(ex: HttpExchange) = respond(ex, 405, "method not allowed", "text/plain; charset=utf-8")
    private fun internalError(ex: HttpExchange) = respond(ex, 500, "internal error", "text/plain; charset=utf-8")

    private fun respond(ex: HttpExchange, code: Int, text: String, contentType: String) {
        val bytes = text.toByteArray(StandardCharsets.UTF_8)
        ex.responseHeaders.set("Content-Type", contentType)
        ex.responseHeaders.set("Cache-Control", "no-store")
        ex.responseHeaders.set("X-Content-Type-Options", "nosniff")
        ex.sendResponseHeaders(code, bytes.size.toLong())
        val os: OutputStream = ex.responseBody
        os.use { it.write(bytes) }
        ex.close()
    }

    private fun normalizeBasePath(value: String): String {
        val normalized = "/" + value.trim().trim('/')
        require(normalized.matches(Regex("/[A-Za-z0-9/_-]*"))) { "Invalid Web API endpoint" }
        return normalized.removeSuffix("/")
    }

    private class FixedWindowRateLimiter(private val limit: Int) {
        private val windows = java.util.concurrent.ConcurrentHashMap<String, Window>()

        fun allow(client: String): Boolean {
            val minute = System.currentTimeMillis() / 60_000L
            val window = windows.compute(client) { _, current ->
                if (current == null || current.minute != minute) Window(minute, 1) else current.apply { count++ }
            } ?: return false
            if (windows.size > 10_000) windows.entries.removeIf { it.value.minute < minute - 1 }
            return window.count <= limit
        }

        private data class Window(val minute: Long, var count: Int)
    }
}

package cc.vastsea.web

import cc.vastsea.storage.Checkins
import cc.vastsea.storage.PlayerStat
import cc.vastsea.storage.Points
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer
import org.bukkit.Bukkit
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.URI
import java.nio.charset.StandardCharsets

class WebApiServer(
    private val address: String,
    private val port: Int,
    private val basePath: String,
) {
    private var server: HttpServer? = null

    fun start() {
        val addr = InetSocketAddress(address, port)
        server = HttpServer.create(addr, 0)
        val s = server ?: return
        s.createContext(path("/ifsignin"), handler { q ->
            val pName = q.param("player") ?: run { badRequest(q, "missing player"); return@handler }
            val player = Bukkit.getOfflinePlayer(pName)
            val v = Checkins.isSignedIn(player.uniqueId)
            okText(q, v.toString())
        })
        s.createContext(path("/total"), handler { q ->
            val pName = q.param("player") ?: run { badRequest(q, "missing player"); return@handler }
            val player = Bukkit.getOfflinePlayer(pName)
            val v = Checkins.getTotalDays(player.uniqueId)
            okText(q, v.toString())
        })
        s.createContext(path("/streak"), handler { q ->
            val pName = q.param("player") ?: run { badRequest(q, "missing player"); return@handler }
            val player = Bukkit.getOfflinePlayer(pName)
            val v = Checkins.getStreakDays(player.uniqueId)
            okText(q, v.toString())
        })
        s.createContext(path("/last_check_in_time"), handler { q ->
            val pName = q.param("player") ?: run { badRequest(q, "missing player"); return@handler }
            val player = Bukkit.getOfflinePlayer(pName)
            val v = Checkins.getLastCheckInTime(player.uniqueId)
            okText(q, v)
        })
        s.createContext(path("/ranktoday"), handler { q ->
            val pName = q.param("player") ?: run { badRequest(q, "missing player"); return@handler }
            val player = Bukkit.getOfflinePlayer(pName)
            val v = Checkins.getRankToday(player.uniqueId)
            okText(q, v)
        })
        s.createContext(path("/points"), handler { q ->
            val pName = q.param("player") ?: run { badRequest(q, "missing player"); return@handler }
            val player = Bukkit.getOfflinePlayer(pName)
            val raw = Points.getPoints(player.uniqueId)
            val display = String.format("%.2f", raw / 100.0)
            okText(q, display)
        })
        s.createContext(path("/info"), handler { q ->
            val pName = q.param("player") ?: run { badRequest(q, "missing player"); return@handler }
            val player = Bukkit.getOfflinePlayer(pName)
            val i = PlayerStat(player.uniqueId)
            val json = "{" +
                    "\"id\":\"${player.uniqueId}\"," +
                    "\"total\":${i.totalDays}," +
                    "\"streak\":${i.streakDays}," +
                    "\"last_check_in_time\":\"${i.lastCheckInTime}\"," +
                    "\"rank_today\":\"${i.rankToday}\"," +
                    // 统一显示为两位小数
                    "\"points\":${String.format("%.2f", i.points / 100.0)}" +
                    "}"
            okJson(q, json)
        })
        s.createContext(path("/amounttoday"), handler { q ->
            val v = Checkins.getAmountToday()
            okText(q, v.toString())
        })

        s.executor = null
        s.start()
    }

    fun stop() {
        server?.stop(0)
        server = null
    }

    private fun path(sub: String): String =
        if (basePath.endsWith("/")) basePath.dropLast(1) + sub else basePath + sub

    private fun handler(block: (Query) -> Unit): HttpHandler = HttpHandler { ex ->
        try {
            if (ex.requestMethod.equals("GET", ignoreCase = true)) {
                block(Query(ex))
            } else {
                methodNotAllowed(ex)
            }
        } catch (t: Throwable) {
            internalError(ex, t.message ?: "internal error")
        }
    }

    private class Query(private val ex: HttpExchange) {
        fun uri(): URI = ex.requestURI
        fun param(key: String): String? {
            val raw = uri().rawQuery ?: return null
            return raw.split("&").mapNotNull {
                val i = it.indexOf('=')
                if (i > 0) it.substring(0, i) to it.substring(i + 1) else null
            }.toMap()[key]
        }

        fun exchange(): HttpExchange = ex
    }

    private fun okText(q: Query, text: String) = respond(q.exchange(), 200, text, "text/plain; charset=utf-8")
    private fun okJson(q: Query, text: String) = respond(q.exchange(), 200, text, "application/json; charset=utf-8")
    private fun badRequest(q: Query, text: String) = respond(q.exchange(), 400, text, "text/plain; charset=utf-8")
    private fun methodNotAllowed(ex: HttpExchange) = respond(ex, 405, "method not allowed", "text/plain; charset=utf-8")
    private fun internalError(ex: HttpExchange, text: String) = respond(ex, 500, text, "text/plain; charset=utf-8")

    private fun respond(ex: HttpExchange, code: Int, text: String, contentType: String) {
        val bytes = text.toByteArray(StandardCharsets.UTF_8)
        ex.responseHeaders.add("Content-Type", contentType)
        ex.sendResponseHeaders(code, bytes.size.toLong())
        val os: OutputStream = ex.responseBody
        os.write(bytes)
        os.flush()
        os.close()
        ex.close()
    }
}
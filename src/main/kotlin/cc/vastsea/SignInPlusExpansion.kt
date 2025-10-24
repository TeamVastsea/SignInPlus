package cc.vastsea

import cc.vastsea.storage.CorrectionSlips
import cc.vastsea.storage.Points
import cc.vastsea.storage.Checkins
import me.clip.placeholderapi.expansion.PlaceholderExpansion
import org.bukkit.Bukkit
import org.bukkit.OfflinePlayer
import org.bukkit.plugin.java.JavaPlugin

class SignInPlusExpansion(private val plugin: JavaPlugin) : PlaceholderExpansion() {
    override fun getIdentifier(): String = "signinplus"
    override fun getAuthor(): String = plugin.description.authors.joinToString(", ")
    override fun getVersion(): String = plugin.description.version

    override fun onRequest(player: OfflinePlayer?, params: String): String? {
        // 统一支持离线玩家：允许通过后缀玩家名查询；若未提供后缀，则使用请求者
        val lower = params.lowercase()

        fun matchWithOptionalName(base: String): String? {
            // base -> 使用请求者；base_<name> -> 使用指定玩家名
            if (lower == base) return player?.name
            if (lower.startsWith("${base}_")) return lower.substring(base.length + 1)
            return null
        }

        // 今日总签到人数（无玩家上下文）
        if (lower == "amount_today") {
            return Checkins.getAmountToday().toString()
        }

        // 今日签到状态（仅保留新键）
        matchWithOptionalName("status")?.let { targetName ->
            val target = Bukkit.getOfflinePlayer(targetName)
            val uuid = target.uniqueId
            return if (Checkins.isSignedIn(uuid)) "&a已签到" else "&c未签到"
        }

        // 玩家相关键（不再支持旧前缀）：total_days, streak_days, last_check_in_time, rank_today, points
        val keys = listOf("total_days", "streak_days", "last_check_in_time", "rank_today", "points")
        for (key in keys) {
            matchWithOptionalName(key)?.let { targetName ->
                val target = Bukkit.getOfflinePlayer(targetName)
                val uuid = target.uniqueId
                return when (key) {
                    "total_days" -> Checkins.getTotalDays(uuid).toString()
                    "streak_days" -> Checkins.getStreakDays(uuid).toString()
                    "last_check_in_time" -> Checkins.getLastCheckInTime(uuid)
                    "rank_today" -> Checkins.getRankToday(uuid)
                    "points" -> String.format("%.2f", Points.getPoints(uuid) / 100.0)
                    else -> null
                }
            }
        }

        // 补签卡数量（仅保留新键 corr）
        matchWithOptionalName("corr")?.let { targetName ->
            val target = Bukkit.getOfflinePlayer(targetName)
            val uuid = target.uniqueId
            return CorrectionSlips.getCorrectionSlipAmount(uuid).toString()
        }

        return null
    }
}
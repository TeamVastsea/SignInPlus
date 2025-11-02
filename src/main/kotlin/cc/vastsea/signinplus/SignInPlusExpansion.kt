package cc.vastsea.signinplus

import cc.vastsea.signinplus.storage.CorrectionSlips
import cc.vastsea.signinplus.storage.Points
import cc.vastsea.signinplus.storage.Checkins
import me.clip.placeholderapi.expansion.PlaceholderExpansion
import org.bukkit.Bukkit
import org.bukkit.OfflinePlayer
import org.bukkit.plugin.java.JavaPlugin
import java.util.UUID

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

        fun resolveUuid(targetName: String?, context: OfflinePlayer?): UUID? {
            // 优先使用上下文玩家（无后缀时）
            if (targetName == null) return context?.uniqueId
            // 在线玩家精确匹配
            plugin.server.getPlayerExact(targetName)?.uniqueId?.let { return it }
            // 仅使用服务端已缓存的离线玩家，避免远程查询导致 NPE
            plugin.server.offlinePlayers.firstOrNull { it.name?.equals(targetName, true) == true }?.uniqueId?.let { return it }
            // 离线模式下可计算离线 UUID；在线模式未知玩家返回 null
            return if (!plugin.server.onlineMode) UUID.nameUUIDFromBytes(("OfflinePlayer:" + targetName).toByteArray(Charsets.UTF_8)) else null
        }

        // 今日总签到人数（无玩家上下文）
        if (lower == "amount_today") {
            return Checkins.getAmountToday().toString()
        }

        // 今日签到状态（仅保留新键）
        matchWithOptionalName("status")?.let { targetName ->
            val uuid = resolveUuid(targetName, player) ?: return SignInPlus.localization.get("commands.status.unknown")
            return if (Checkins.isSignedIn(uuid)) "§a" + SignInPlus.localization.get("commands.status.signed_in") else "§c" + SignInPlus.localization.get("commands.status.not_signed_in")
        }

        // 玩家相关键（不再支持旧前缀）：total_days, streak_days, last_check_in_time, rank_today, points
        val keys = listOf("total_days", "streak_days", "last_check_in_time", "rank_today", "points")
        for (key in keys) {
            matchWithOptionalName(key)?.let { targetName ->
                val uuid = resolveUuid(targetName, player) ?: when (key) {
                    "total_days" -> return "0"
                    "streak_days" -> return "0"
                    "last_check_in_time" -> return "-"
                    "rank_today" -> return "-"
                    "points" -> return "0.00"
                    else -> return null
                }
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
            val uuid = resolveUuid(targetName, player) ?: return "0"
            return CorrectionSlips.getCorrectionSlipAmount(uuid).toString()
        }

        return null
    }
}
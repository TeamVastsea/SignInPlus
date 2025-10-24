package cc.vastsea

import cc.vastsea.storage.Checkins
import cc.vastsea.storage.PlayerStat
import cc.vastsea.storage.Points
import cc.vastsea.storage.CorrectionSlips
import me.clip.placeholderapi.expansion.PlaceholderExpansion
import org.bukkit.OfflinePlayer
import org.bukkit.Bukkit

class SignInPlusExpansion(private val plugin: SignInPlus) : PlaceholderExpansion() {
    override fun getIdentifier(): String = "signinplus"
    override fun getAuthor(): String = "Snowball_233, zrll_"
    override fun getVersion(): String = plugin.description.version

    override fun onRequest(player: OfflinePlayer?, params: String): String? {
        val baseName = player?.name ?: ""
        val lower = params.lowercase()

        fun matchWithOptionalName(base: String): String? {
            return when {
                lower == base -> baseName
                lower.startsWith("${base}_") && params.length > base.length + 1 -> params.substring(base.length + 1)
                else -> null
            }
        }

        // 特例：无需玩家名的全局统计
        if (lower == "check_in_amount_today") {
            return Checkins.getAmountToday().toString()
        }

        // 状态占位符（无后缀时默认当前玩家；支持离线玩家名）
        matchWithOptionalName("check_in_status")?.let { target ->
            val offline = Bukkit.getOfflinePlayer(target)
            return if (Checkins.isSignedIn(offline.uniqueId)) "&a已签到" else "&c未签到"
        }

        // 支持带玩家名的占位符：total_days / streak_days / correction_slip_amount / last_check_in时间 / rank_today / points
        val keys = listOf(
            "check_in_total_days",
            "check_in_streak_days",
            "check_in_last_check_in_time",
            "check_in_rank_today",
            "check_in_points"
        )

        val correctionKey = "check_in_correction_slip_amount"

        for (key in keys) {
            matchWithOptionalName(key)?.let { target ->
                val offline = Bukkit.getOfflinePlayer(target)
                val uuid = offline.uniqueId
                return when (key) {
                    "check_in_total_days" -> Checkins.getTotalDays(uuid).toString()
                    "check_in_streak_days" -> Checkins.getStreakDays(uuid).toString()
                    "check_in_last_check_in_time" -> Checkins.getLastCheckInTime(uuid)
                    "check_in_rank_today" -> Checkins.getRankToday(uuid)
                    // 统一显示为 points/100，保留两位小数
                    "check_in_points" -> String.format("%.2f", Points.getPoints(uuid) / 100.0)
                    else -> null
                }
            }
        }

        matchWithOptionalName(correctionKey)?.let { target ->
            val offline = Bukkit.getOfflinePlayer(target)
            val uuid = offline.uniqueId
            return CorrectionSlips.getCorrectionSlipAmount(uuid).toString()
        }

        return null
    }
}
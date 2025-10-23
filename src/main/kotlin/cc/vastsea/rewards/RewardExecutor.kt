package cc.vastsea.rewards

import cc.vastsea.SignInPlus
import cc.vastsea.storage.Checkins
import cc.vastsea.storage.ClaimedRewards
import cc.vastsea.storage.Points
import cc.vastsea.storage.SpecialDateClaims
import org.bukkit.Bukkit
import org.bukkit.Material

import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import java.time.LocalDate
import java.util.UUID
import kotlin.random.Random

class RewardExecutor(private val plugin: SignInPlus) {
    private val prefix = (plugin.config.getString("message_prefix") ?: "&7[&a签到Plus&7] ").replace('&', '§')
    private val logger = DebugLogger(plugin)
    private val actionsRunner = ActionsRunner(plugin, prefix)
    private val isDebug = plugin.config.getBoolean("debug", false)

    private fun playerLabel(id: UUID): String {
        val name = plugin.server.getPlayer(id)?.name ?: plugin.server.getOfflinePlayer(id)?.name ?: "未知玩家"
        return "&b$name&7(&e$id&7)"
    }
    private fun colorizeForConsole(message: String): String {
        val colorMap = mapOf(
            "&0" to "\u001B[0;30m", // Black
            "&1" to "\u001B[0;34m", // Dark Blue
            "&2" to "\u001B[0;32m", // Dark Green
            "&3" to "\u001B[0;36m", // Dark Aqua
            "&4" to "\u001B[0;31m", // Dark Red
            "&5" to "\u001B[0;35m", // Dark Purple
            "&6" to "\u001B[0;33m", // Gold
            "&7" to "\u001B[0;37m", // Gray
            "&8" to "\u001B[0;90m", // Dark Gray
            "&9" to "\u001B[0;94m", // Blue
            "&a" to "\u001B[0;92m", // Green
            "&b" to "\u001B[0;96m", // Aqua
            "&c" to "\u001B[0;91m", // Red
            "&d" to "\u001B[0;95m", // Light Purple
            "&e" to "\u001B[0;93m", // Yellow
            "&f" to "\u001B[0;97m", // White
            "&r" to "\u001B[0m"      // Reset
        )
        var coloredMessage = message
        for ((key, value) in colorMap) {
            coloredMessage = coloredMessage.replace(key, value)
        }
        return coloredMessage + "\u001B[0m" // Ensure reset at the end
    }

    private fun logDebug(message: String) {
        logger.info(message)
    }

    fun onSignedIn(player: UUID) {
        logDebug("Processing sign-in for player: ${playerLabel(player)}")
        // 默认奖励
        runActionsFromConfig("default.actions", player)

        // 累计奖励
        Checkins.getSignedDates(player).let { signedDates ->
            val total = signedDates.size
            runCumulativeRewards(total, player)

            // 连续奖励
            val streak = checkStreak(signedDates)
            runStreakRewards(streak, player)
        }

        // 排行奖励
        val rankStr = Checkins.getRankToday(player)
        val rank = rankStr.toIntOrNull()
        if (rank != null) {
            runTopRewards(rank, player)
        }

        // 特殊日期奖励
        val specials = plugin.config.getMapList("special_dates")
        val zone = java.time.ZoneId.of(plugin.config.getString("timezone") ?: "Asia/Shanghai")
        val now = java.time.LocalDate.now(zone)
        val dow = now.dayOfWeek.name // e.g. THURSDAY
        val mdFmt = java.time.format.DateTimeFormatter.ofPattern("MM-dd")

        for (m in specials) {
            val date = m["date"] as? String ?: continue
            val actions = m["actions"] as? List<*> ?: continue
            val repeatEnabled = (m["repeat"] as? Boolean) ?: false
            val repeatTime = if (repeatEnabled) ((m["repeat_time"] as? Number)?.toInt() ?: 1) else 1

            val isExact = date.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))
            val isYearly = date.matches(Regex("\\*-\\d{2}-\\d{2}"))
            val isMonthly = date.matches(Regex("\\*-\\*-\\d{2}"))
            val isWeekday = listOf("Monday","Tuesday","Wednesday","Thursday","Friday","Saturday","Sunday").any { it.equals(date, true) }

            val match = when {
                // 每年的某一天: *-MM-dd
                isYearly -> date.substring(2) == now.format(mdFmt)
                // 每月的某一天: *-*-dd
                isMonthly -> date.substring(4) == String.format("%02d", now.dayOfMonth)
                // 星期几: Monday..Sunday (大小写不敏感)
                isWeekday -> dow.equals(date.uppercase(), true)
                // 精确日期 yyyy-MM-dd
                isExact -> date == now.toString()
                else -> false
            }

            if (!match) continue

            if (!isExact) {
                val limit = if (repeatEnabled) repeatTime.coerceAtLeast(1) else 1
                val currentTimes = SpecialDateClaims.getTimes(player, date)
                if (currentTimes >= limit) {
                    logDebug("Special date '&a$date&r' reached limit for ${playerLabel(player)} (&c$currentTimes/$limit&r), skipping.")
                    continue
                }
                // 允许一次领取，并累计次数
                logDebug("Granting special date '&a$date&r' to ${playerLabel(player)} (count &a${currentTimes + 1}/$limit&r). Actions: &c${actions.joinToString(", ")}")
                runActionLines(actions, player)
                SpecialDateClaims.increment(player, date)
            } else {
                // 精确日期仅当天一次，不需要次数限制
                logDebug("Granting special date '&a$date&r' to ${playerLabel(player)} (exact date). Actions: &c${actions.joinToString(", ")}")
                runActionLines(actions, player)
            }
        }
    }

    fun runDefaultRewards(player: UUID) {
        runActionsFromConfig("default.actions", player)
    }

    fun runCumulativeRewards(totalDays: Int, player: UUID, force: Boolean = false) {
        val list = plugin.config.getMapList("cumulative")
        if (list.isEmpty()) return
        val enabled = list.firstOrNull()?.get("enable") as? Boolean
            ?: list.find { it.containsKey("enable") }?.get("enable") as? Boolean
            ?: false
        if (!enabled && !force) return
        logDebug("Checking cumulative rewards for ${playerLabel(player)} &r(Total Days: &a$totalDays&r)")
        val eligible = list.filter { it.containsKey("times") }
            .mapNotNull { m ->
                val threshold = m["times"] as? Int ?: return@mapNotNull null
                if (totalDays >= threshold && !ClaimedRewards.hasClaimedTotalReward(player, threshold)) threshold to m else null
            }
        eligible.forEach { (threshold, rewardMap) ->
            val actions = (rewardMap["actions"] as? List<*>) ?: emptyList<Any>()
            logDebug("Granting cumulative reward for &a$threshold &rdays to ${playerLabel(player)}. Actions: &c${actions.joinToString(", ")}")
            runActionLines(actions, player)
            if (!force) {
                ClaimedRewards.markClaimedTotalReward(player, threshold)
            }
        }
    }

    private fun checkStreak(dates: List<LocalDate>): Int {
        val today = LocalDate.now(java.time.ZoneId.of(plugin.config.getString("timezone") ?: "Asia/Shanghai"))
        var streak = 0
        var currentDate = today

        // 如果今天还没签到，就从昨天开始算
        if (!dates.contains(today)) {
            currentDate = today.minusDays(1)
        }

        while (dates.contains(currentDate)) {
            streak++
            currentDate = currentDate.minusDays(1)
        }
        return streak
    }

    fun runStreakRewards(streakDays: Int, player: UUID, force: Boolean = false) {
        val list = plugin.config.getMapList("streak")
        if (list.isEmpty()) return
        val enabled = list.firstOrNull()?.get("enable") as? Boolean
            ?: list.find { it.containsKey("enable") }?.get("enable") as? Boolean
            ?: false
        if (!enabled && !force) return
        logDebug("Checking streak rewards for ${playerLabel(player)} &r(Streak: &a$streakDays &rdays)")
        val eligible = list.filter { it.containsKey("times") }
            .mapNotNull { m ->
                val threshold = m["times"] as? Int ?: return@mapNotNull null
                if (streakDays >= threshold && !ClaimedRewards.hasClaimedStreakReward(player, threshold)) threshold to m else null
            }
        eligible.forEach { (threshold, rewardMap) ->
            val actions = (rewardMap["actions"] as? List<*>) ?: emptyList<Any>()
            logDebug("Granting streak reward for &a$threshold &rdays to ${playerLabel(player)}. Actions: &c${actions.joinToString(", ")}")
            runActionLines(actions, player)
            if (!force) {
                ClaimedRewards.markClaimedStreakReward(player, threshold)
            }
        }
    }

    fun runTopRewards(rank: Int, player: UUID, force: Boolean = false) {
        val list = plugin.config.getMapList("top")
        if (list.isEmpty()) return
        val enabled = list.firstOrNull()?.get("enable") as? Boolean
            ?: list.find { it.containsKey("enable") }?.get("enable") as? Boolean
            ?: false
        if (!enabled && !force) return
        logDebug("Checking top rewards for ${playerLabel(player)} &r(Rank: &a$rank&r)")
        for (m in list) {
            val r = m["rank"] as? Int ?: continue
            if (rank == r) {
                val actions = m["actions"] as? List<*> ?: continue
                logDebug("Granting top reward for rank &a$rank &rto ${playerLabel(player)}. Actions: &c${actions.joinToString(", ")}")
                runActionLines(actions, player)
                break
            }
        }
    }

    fun runSpecialDateRewards(dateStr: String, player: UUID, simulatedPrevTimes: Int? = null) {
        val specials = plugin.config.getMapList("special_dates")

        val isExactInput = dateStr.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))
        val isYearlyInput = dateStr.matches(Regex("\\*-\\d{2}-\\d{2}"))
        val isMonthlyInput = dateStr.matches(Regex("\\*-\\*-\\d{2}"))
        val isWeekdayInput = listOf("Monday","Tuesday","Wednesday","Thursday","Friday","Saturday","Sunday").any { it.equals(dateStr, true) }

        // debug 模式：如果传入的是配置里的模式（如 *-*-14 或 Thursday），直接匹配相同配置项，不校验今天
        for (m in specials) {
            val date = m["date"] as? String ?: continue
            val actions = m["actions"] as? List<*> ?: continue
            val repeatEnabled = (m["repeat"] as? Boolean) ?: false
            val repeatTime = if (repeatEnabled) ((m["repeat_time"] as? Number)?.toInt() ?: 1) else 1

            val shouldRun = when {
                isExactInput -> date == dateStr // 精确日期按照目标日期匹配
                isYearlyInput || isMonthlyInput || isWeekdayInput -> date.equals(dateStr, true) // 模式/星期：字符串精确匹配
                else -> false
            }
            if (!shouldRun) continue

            val isExactCfg = date.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))
            val isPatternCfg = !isExactCfg

            if (isPatternCfg) {
                val limit = if (repeatEnabled) repeatTime.coerceAtLeast(1) else 1
                val prev = simulatedPrevTimes ?: 0
                if (prev >= limit) {
                    logDebug("[Debug] Special date '&a$date&r' reached limit for ${playerLabel(player)} (&c$prev/$limit&r), skipping.")
                    continue
                }
                logDebug("[Debug] Granting special date '&a$date&r' to ${playerLabel(player)} (count &a${prev + 1}/$limit&r). Actions: &c${actions.joinToString(", ")}")
                runActionLines(actions, player)
                // Debug: 不记录或修改数据库中的特殊日期次数
            } else {
                logDebug("[Debug] Granting special date '&a$date&r' to ${playerLabel(player)} (exact date). Actions: &c${actions.joinToString(", ")}")
                runActionLines(actions, player)
            }
        }
    }

    private fun runActionsFromConfig(path: String, player: UUID) {
        val actions = plugin.config.getStringList(path)
        if (actions.isEmpty()) return
        logDebug("Running actions from config path '&a$path&r' for player ${playerLabel(player)}. Actions: &c${actions.joinToString(", ")}")
        actionsRunner.runActionLines(actions, player)
    }

    private fun runActionLines(lines: List<*>, player: UUID) {
        actionsRunner.runActionLines(lines, player)
    }


}
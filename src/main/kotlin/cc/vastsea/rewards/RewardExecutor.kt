package cc.vastsea.rewards

import cc.vastsea.SignInPlus
import cc.vastsea.storage.Checkins
import cc.vastsea.storage.ClaimedRewards
import cc.vastsea.storage.Points
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.ChatColor
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import java.time.LocalDate
import java.util.UUID
import kotlin.random.Random

class RewardExecutor(private val plugin: SignInPlus) {
    private val prefix = ChatColor.translateAlternateColorCodes('&', plugin.config.getString("message_prefix") ?: "&7[&a签到Plus&7] ")
    private val isDebug = plugin.config.getBoolean("debug", false)

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
        if (isDebug) {
            plugin.logger.info(colorizeForConsole("&e[Debug] &r$message"))
        }
    }

    fun onSignedIn(player: UUID) {
        logDebug("Processing sign-in for player: &b$player")
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
        val yyyyMmFmt = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-")

        for (m in specials) {
            val date = m["date"] as? String ?: continue
            val actions = m["actions"] as? List<*> ?: continue
            val repeatEnabled = (m["repeat"] as? Boolean) ?: false
            val repeatTime = if (repeatEnabled) ((m["repeat_time"] as? Number)?.toInt() ?: 1) else 1

            val match = when {
                // 每年的某一天: *-MM-dd
                date.matches(Regex("\\*-\\d{2}-\\d{2}")) -> date.substring(2) == now.format(mdFmt)
                // 每月的某一天: *-*-dd
                date.matches(Regex("\\*-\\*-\\d{2}")) -> date.substring(4) == String.format("%02d", now.dayOfMonth)
                // 星期几: Monday..Sunday (大小写不敏感)
                date.equals("Monday", true) || date.equals("Tuesday", true) || date.equals("Wednesday", true) ||
                        date.equals("Thursday", true) || date.equals("Friday", true) || date.equals("Saturday", true) ||
                        date.equals("Sunday", true) -> dow.equals(date.uppercase(), true)
                // 精确日期 yyyy-MM-dd
                date.matches(Regex("\\d{4}-\\d{2}-\\d{2}")) -> date == now.toString()
                else -> false
            }

            if (match) {
                if (isDebug) {
                    plugin.logger.info("[Debug] Matched special date '$date' for player $player. Repeating $repeatTime time(s). Actions: ${actions.joinToString(", ")}")
                }
                repeat(repeatTime.coerceAtLeast(1)) {
                    runActionLines(actions, player)
                }
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
        logDebug("Checking cumulative rewards for &b$player &r(Total Days: &a$totalDays&r)")
        val eligible = list.filter { it.containsKey("times") }
            .mapNotNull { m ->
                val threshold = m["times"] as? Int ?: return@mapNotNull null
                if (totalDays >= threshold && !ClaimedRewards.hasClaimedTotalReward(player, threshold)) threshold to m else null
            }
        eligible.forEach { (threshold, rewardMap) ->
            val actions = (rewardMap["actions"] as? List<*>) ?: emptyList<Any>()
            logDebug("Granting cumulative reward for &a$threshold &rdays to &b$player&r. Actions: &c${actions.joinToString(", ")}")
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
        logDebug("Checking streak rewards for &b$player &r(Streak: &a$streakDays &rdays)")
        val eligible = list.filter { it.containsKey("times") }
            .mapNotNull { m ->
                val threshold = m["times"] as? Int ?: return@mapNotNull null
                if (streakDays >= threshold && !ClaimedRewards.hasClaimedStreakReward(player, threshold)) threshold to m else null
            }
        eligible.forEach { (threshold, rewardMap) ->
            val actions = (rewardMap["actions"] as? List<*>) ?: emptyList<Any>()
            logDebug("Granting streak reward for &a$threshold &rdays to &b$player&r. Actions: &c${actions.joinToString(", ")}")
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
        logDebug("Checking top rewards for &b$player &r(Rank: &a$rank&r)")
        for (m in list) {
            val r = m["rank"] as? Int ?: continue
            if (rank == r) {
                val actions = m["actions"] as? List<*> ?: continue
                logDebug("Granting top reward for rank &a$rank &rto &b$player&r. Actions: &c${actions.joinToString(", ")}")
                runActionLines(actions, player)
                break
            }
        }
    }

    fun runSpecialDateRewards(dateStr: String, player: UUID) {
        val specials = plugin.config.getMapList("special_dates")
        val zone = java.time.ZoneId.of(plugin.config.getString("timezone") ?: "Asia/Shanghai")
        val today = java.time.LocalDate.now(zone)
        val mdFmt = java.time.format.DateTimeFormatter.ofPattern("MM-dd")

        val isExact = dateStr.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))
        val isYearly = dateStr.matches(Regex("\\*-\\d{2}-\\d{2}"))
        val isMonthly = dateStr.matches(Regex("\\*-\\*-\\d{2}"))
        val isWeekday = listOf("Monday","Tuesday","Wednesday","Thursday","Friday","Saturday","Sunday").any { it.equals(dateStr, true) }

        // debug 模式：如果传入的是配置里的模式（如 *-*-14 或 Thursday），直接触发与该模式相同的条目，而不校验今天是否匹配
        for (m in specials) {
            val date = m["date"] as? String ?: continue
            val actions = m["actions"] as? List<*> ?: continue
            val repeatEnabled = (m["repeat"] as? Boolean) ?: false
            val repeatTime = if (repeatEnabled) ((m["repeat_time"] as? Number)?.toInt() ?: 1) else 1

            val shouldRun = when {
                isExact -> date == dateStr // 精确日期按照目标日期匹配
                isYearly || isMonthly || isWeekday -> date.equals(dateStr, true) // 模式/星期：按配置项字符串精确匹配
                else -> false
            }

            if (shouldRun) {
                logDebug("Matched special date '&a$date&r' for player &b$player&r. Repeating &a$repeatTime &rtime(s). Actions: &c${actions.joinToString(", ")}")
                repeat(repeatTime.coerceAtLeast(1)) {
                    runActionLines(actions, player)
                }
            }
        }
    }

    private fun runActionsFromConfig(path: String, player: UUID) {
        val actions = plugin.config.getStringList(path)
        if (actions.isEmpty()) return
        logDebug("Running actions from config path '&a$path&r' for player &b$player&r. Actions: &c${actions.joinToString(", ")}")
        runActionLines(actions, player)
    }

    private fun runActionLines(lines: List<*>, player: UUID) {
        // 将动作顺序执行；SLEEP 使用延迟调度
        var delayTicks = 0L
        var i = 0
        while (i < lines.size) {
            val current = lines[i]
            val raw = if (current is String) current.trim() else null
            if (raw == null) { i++; continue }

            // 处理随机互斥抽取块
            if (raw.startsWith("[RANDOM_PICK=")) {
                val n = raw.substringAfter("[RANDOM_PICK=").substringBefore("]").toIntOrNull() ?: 1
                val block = mutableListOf<String>()
                var j = i + 1
                while (j < lines.size) {
                    val s = (lines[j] as? String)?.trim() ?: break
                    if (s.startsWith("[/RANDOM_PICK]")) break
                    block += s
                    j++
                }
                val chosen = block.shuffled().take(n.coerceAtMost(block.size))
                chosen.forEach { act -> scheduleAction(act, player, delayTicks) }
                i = j + 1
                continue
            }

            // 处理随机权重块
            if (raw.startsWith("[RANDOM_WEIGHTED]")) {
                val weighted = mutableListOf<Pair<Int, String>>()
                var j = i + 1
                while (j < lines.size) {
                    val s = (lines[j] as? String)?.trim() ?: break
                    if (s.startsWith("[/RANDOM_WEIGHTED]")) break
                    val w = Regex("^\\[WEIGHT=(\\d+)\\]\\s*(.*)$").find(s)
                    val weight = w?.groupValues?.get(1)?.toIntOrNull() ?: 1
                    val act = w?.groupValues?.get(2) ?: s
                    weighted += weight to act
                    j++
                }
                val sum = weighted.sumOf { it.first }
                if (sum > 0) {
                    var r = Random.nextInt(sum)
                    var picked: String? = null
                    for ((w, act) in weighted) {
                        if (r < w) { picked = act; break } else r -= w
                    }
                    picked?.let { scheduleAction(it, player, delayTicks) }
                }
                i = j + 1
                continue
            }

            // 概率与常规动作
            if (raw.startsWith("[PROB=")) {
                val prob = raw.substringAfter("[PROB=").substringBefore("]").toDoubleOrNull() ?: 1.0
                val act = raw.substringAfter("]").trim()
                if (Random.nextDouble() <= prob) scheduleAction(act, player, delayTicks)
                i++
                continue
            }

            if (raw.startsWith("[SLEEP]")) {
                val ticks = raw.substringAfter("[SLEEP]").trim().toIntOrNull() ?: 0
                delayTicks += ticks
                i++
                continue
            }

            scheduleAction(raw, player, delayTicks)
            i++
        }
    }

    private fun scheduleAction(action: String, player: UUID, delayTicks: Long) {
        Bukkit.getScheduler().runTaskLater(plugin, Runnable { runSingleAction(action, player) }, delayTicks)
    }

    private fun runSingleAction(action: String, player: UUID) {
        val server = plugin.server
        val player = server.getPlayer(player)

        when {
            action.startsWith("[COMMAND]") -> {
                val cmd = action.substringAfter("[COMMAND]").trim().replace("%player_name%", player?.displayName ?: "")
                server.dispatchCommand(server.consoleSender, cmd)
            }
            action.startsWith("[MESSAGE]") -> {
                player?.sendMessage(prefix + ChatColor.translateAlternateColorCodes('&', action.substringAfter("[MESSAGE]").trim()))
            }
            action.startsWith("[TITLE]") -> {
                val parts = action.substringAfter("[TITLE]").trim().split("|", limit = 2)
                val title = ChatColor.translateAlternateColorCodes('&', parts.getOrNull(0) ?: "")
                val sub = ChatColor.translateAlternateColorCodes('&', parts.getOrNull(1) ?: "")
                player?.sendTitle(title, sub, 10, 60, 10)
            }
            action.startsWith("[BROADCAST]") -> {
                val msg = ChatColor.translateAlternateColorCodes('&', action.substringAfter("[BROADCAST]").trim().replace("%player_name%", player?.displayName ?:""))
                server.broadcastMessage(prefix + msg)
            }
            action.startsWith("[SOUND]") -> {
                val args = action.substringAfter("[SOUND]").trim().split(" ")
                val type = args.getOrNull(0)?.uppercase() ?: return
                val vol = args.getOrNull(1)?.toFloatOrNull() ?: 1.0f
                val pitch = args.getOrNull(2)?.toFloatOrNull() ?: 1.0f
                val sound = runCatching { Sound.valueOf(type) }.getOrNull() ?: return
                player?.playSound(player.location, sound, vol, pitch)
            }
            action.startsWith("[EFFECT]") -> {
                val args = action.substringAfter("[EFFECT]").trim().split(" ")
                val typeName = args.getOrNull(0)?.uppercase() ?: return
                val level = (args.getOrNull(1)?.toIntOrNull() ?: 1).coerceAtLeast(1)
                val seconds = (args.getOrNull(2)?.toIntOrNull() ?: 5).coerceAtLeast(1)
                val type = PotionEffectType.getByName(typeName) ?: return
                player?.addPotionEffect(PotionEffect(type, seconds * 20, level - 1))
            }
            action.startsWith("[ITEM]") -> {
                val spec = action.substringAfter("[ITEM]").trim()
                val parts = spec.split(" ")
                var itemKey = parts.getOrNull(0) ?: return
                val amount = parts.getOrNull(1)?.toIntOrNull() ?: 1
                val nbtAndFlags = if (parts.size > 2) parts.drop(2).joinToString(" ") else null

                var nbt: String? = nbtAndFlags
                var force = false
                if (nbt != null) {
                    val forceRegex = Regex("\\s+force=true$", RegexOption.IGNORE_CASE)
                    if (forceRegex.containsMatchIn(nbt)) {
                        force = true
                        nbt = forceRegex.replace(nbt, "")
                    }
                }

                val cleanNbt = nbt?.trim()?.removeSurrounding("\"")

                if (itemKey.contains(":")) itemKey = itemKey.substringAfter(":")
                val mat = Material.matchMaterial(itemKey.uppercase()) ?: return
                val stack = ItemStack(mat, amount)
                if (!cleanNbt.isNullOrBlank()) {
                    applyNbtSafely(stack, cleanNbt, force, player)
                }
                player?.inventory?.addItem(stack)
            }
            action.startsWith("[POINTS]") -> {
                val spec = action.substringAfter("[POINTS]").trim()
                val value = parsePointsValue(spec)
                // 以“分”为单位存储：显示时除以100；存储时乘以100并四舍五入为整数
                val cents = kotlin.math.round(value * 100.0)
                if (player == null) return
                Points.addPoints(player.uniqueId, cents)
            }
        }
    }

    private fun applyNbtSafely(stack: ItemStack, nbt: String, force: Boolean, player: Player?) {
        var processedNbt = nbt.trim()
        if (force) {
            // Strip existing braces and wrap with a new pair to ensure it's a valid object.
            processedNbt = "{${processedNbt.removeSurrounding("{", "}").trim()}}"
        }

        runCatching {
            Bukkit.getUnsafe().modifyItemStack(stack, processedNbt)
        }.onFailure { e ->
            val errorMessage = prefix + "Failed to parse item NBT: ${e.cause?.message ?: e.message}"
            player?.sendMessage(errorMessage)
            plugin.logger.warning("NBT parse error for '$nbt' (processed as '$processedNbt'): ${e.message}")
        }
    }

    private fun parsePointsValue(spec: String): Double {
        // 支持： "10" | "1..5" | "1..5 z" | "1..5 .2f"
        val parts = spec.split(" ")
        val range = parts.getOrNull(0) ?: "0"
        val fmt = parts.getOrNull(1)
        val value = if (range.contains("..")) {
            val a = range.substringBefore("..").toDoubleOrNull() ?: 0.0
            val b = range.substringAfter("..").toDoubleOrNull() ?: a
            val x = a + Random.nextDouble() * (b - a)
            x
        } else range.toDoubleOrNull() ?: 0.0

        return when {
            fmt == null -> value.toDouble()
            fmt == "z" -> kotlin.math.round(value).toDouble()
            fmt.matches(Regex("\\.\\df")) -> {
                var digits = fmt.substring(1, fmt.length - 1).toIntOrNull() ?: 2
                // 要求：.3f及以上按.2f处理；.1f保持一位小数随机
                if (digits >= 3) digits = 2
                String.format("%.${digits}f", value).toDouble()
            }
            else -> value
        }
    }
}
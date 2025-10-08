package cc.vastsea.rewards

import cc.vastsea.SignInPlus
import cc.vastsea.storage.SqliteStorage
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.ChatColor
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import java.time.LocalDate
import kotlin.random.Random

class RewardExecutor(private val plugin: SignInPlus) {
    private val prefix = ChatColor.translateAlternateColorCodes('&', plugin.config.getString("message_prefix") ?: "&7[&a签到Plus&7] ")

    fun onSignedIn(playerName: String) {
        // 默认奖励
        runActionsFromConfig("default.actions", playerName)

        if (plugin.storage is SqliteStorage) {
            val s = plugin.storage as SqliteStorage
            val signedDates = s.getSignedDates(playerName)

            // 累计奖励
            val total = signedDates.size
            runCumulativeRewards(total, playerName)

            // 连续奖励
            val streak = checkStreak(signedDates)
            runStreakRewards(streak, playerName)
        }

        // 排行奖励（列表结构：第一个元素可为 enable，后续为 { rank, actions }）
        val rankStr = if (plugin.storage is SqliteStorage) (plugin.storage as SqliteStorage).getRankToday(playerName) else plugin.storage.getInfo(playerName).rankToday
        val rank = rankStr.toIntOrNull()
        if (rank != null) {
            runTopRewards(rank, playerName)
        }

        // 特殊日期奖励
        val specials = plugin.config.getMapList("special_dates")
        if (specials.isNotEmpty()) {
            val now = java.time.LocalDate.now(java.time.ZoneId.of(plugin.config.getString("timezone") ?: "Asia/Shanghai"))
            val dayOfWeek = now.dayOfWeek.name // e.g. THURSDAY
            for (m in specials) {
                val dateRaw = m["date"] as? String ?: continue
                val date = dateRaw.trim()
                val actions = m["actions"] as? List<*> ?: emptyList<Any>()
                val repeat = m["repeat"] as? Boolean ?: false
                val repeatTime = m["repeat_time"] as? Int ?: 1
                val match = when {
                    date.matches(Regex("\\d{4}-\\d{2}-\\d{2}")) -> date == now.toString()
                    date.matches(Regex("\\*-\\d{2}-\\d{2}")) -> date.substring(2) == now.toString().substring(5)
                    date.matches(Regex("\\*-\\*-\\d{2}")) -> date.substring(4) == String.format("%02d", now.dayOfMonth)
                    else -> date.equals(dayOfWeek.lowercase().replaceFirstChar { it.uppercase() }, ignoreCase = true)
                }
                if (match) {
                    repeat(repeatTime.coerceAtLeast(1)) {
                        runActionLines(actions, playerName)
                    }
                }
            }
        }
    }

    private fun runCumulativeRewards(totalDays: Int, playerName: String) {
        val list = plugin.config.getMapList("cumulative")
        if (list.isEmpty()) return
        val enabled = list.firstOrNull()?.get("enable") as? Boolean
            ?: list.find { it.containsKey("enable") }?.get("enable") as? Boolean
            ?: false
        if (!enabled) return
        val eligible = list.filter { it.containsKey("times") }
            .mapNotNull { m ->
                val threshold = m["times"] as? Int ?: return@mapNotNull null
                if (totalDays >= threshold && !plugin.storage.hasClaimedTotalReward(playerName, threshold)) threshold to m else null
            }
        eligible.forEach { (threshold, rewardMap) ->
            val actions = (rewardMap["actions"] as? List<*>) ?: emptyList<Any>()
            runActionLines(actions, playerName)
            plugin.storage.markClaimedTotalReward(playerName, threshold)
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

    private fun runStreakRewards(streakDays: Int, playerName: String) {
        val list = plugin.config.getMapList("streak")
        if (list.isEmpty()) return
        val enabled = list.firstOrNull()?.get("enable") as? Boolean
            ?: list.find { it.containsKey("enable") }?.get("enable") as? Boolean
            ?: false
        if (!enabled) return
        val eligible = list.filter { it.containsKey("times") }
            .mapNotNull { m ->
                val threshold = m["times"] as? Int ?: return@mapNotNull null
                if (streakDays >= threshold && !plugin.storage.hasClaimedStreakReward(playerName, threshold)) threshold to m else null
            }
        eligible.forEach { (threshold, rewardMap) ->
            val actions = (rewardMap["actions"] as? List<*>) ?: emptyList<Any>()
            runActionLines(actions, playerName)
            plugin.storage.markClaimedStreakReward(playerName, threshold)
        }
    }

    private fun runTopRewards(rank: Int, playerName: String) {
        val list = plugin.config.getMapList("top")
        if (list.isEmpty()) return
        val enabled = list.firstOrNull()?.get("enable") as? Boolean
            ?: list.find { it.containsKey("enable") }?.get("enable") as? Boolean
            ?: false
        if (!enabled) return
        for (m in list) {
            val r = m["rank"] as? Int ?: continue
            if (rank == r) {
                val actions = m["actions"] as? List<*> ?: continue
                runActionLines(actions, playerName)
                break
            }
        }
    }

    private fun runActionsFromConfig(path: String, playerName: String) {
        val actions = plugin.config.getStringList(path)
        if (actions.isEmpty()) return
        runActionLines(actions, playerName)
    }

    private fun runActionLines(lines: List<*>, playerName: String) {
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
                chosen.forEach { act -> scheduleAction(act, playerName, delayTicks) }
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
                    picked?.let { scheduleAction(it, playerName, delayTicks) }
                }
                i = j + 1
                continue
            }

            // 概率与常规动作
            if (raw.startsWith("[PROB=")) {
                val prob = raw.substringAfter("[PROB=").substringBefore("]").toDoubleOrNull() ?: 1.0
                val act = raw.substringAfter("]").trim()
                if (Random.nextDouble() <= prob) scheduleAction(act, playerName, delayTicks)
                i++
                continue
            }

            if (raw.startsWith("[SLEEP]")) {
                val ticks = raw.substringAfter("[SLEEP]").trim().toIntOrNull() ?: 0
                delayTicks += ticks
                i++
                continue
            }

            scheduleAction(raw, playerName, delayTicks)
            i++
        }
    }

    private fun scheduleAction(action: String, playerName: String, delayTicks: Long) {
        Bukkit.getScheduler().runTaskLater(plugin, Runnable { runSingleAction(action, playerName) }, delayTicks)
    }

    private fun runSingleAction(action: String, playerName: String) {
        val server = plugin.server
        val player: Player? = server.getPlayerExact(playerName)

        when {
            action.startsWith("[COMMAND]") -> {
                val cmd = action.substringAfter("[COMMAND]").trim().replace("%player_name%", playerName)
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
                val msg = ChatColor.translateAlternateColorCodes('&', action.substringAfter("[BROADCAST]").trim().replace("%player_name%", playerName))
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
                if (plugin.storage is SqliteStorage) {
                    (plugin.storage as SqliteStorage).addPoints(playerName, cents.toDouble())
                } else {
                    val old = plugin.storage.getPoints(playerName)
                    if (plugin.storage is SqliteStorage) {
                        (plugin.storage as SqliteStorage).setPoints(playerName, old + cents)
                    }
                }
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
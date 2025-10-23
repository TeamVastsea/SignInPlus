package cc.vastsea.rewards

import cc.vastsea.SignInPlus
import cc.vastsea.storage.Points
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import java.util.UUID
import kotlin.random.Random

class ActionsRunner(private val plugin: SignInPlus, private val prefix: String) {
    fun runActionLines(lines: List<*>, player: UUID) {
        var delayTicks = 0L
        var i = 0
        while (i < lines.size) {
            val current = lines[i]
            val raw = if (current is String) current.trim() else null
            if (raw == null) { i++; continue }

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
        val p = server.getPlayer(player)

        when {
            action.startsWith("[COMMAND]") -> {
                val cmd = action.substringAfter("[COMMAND]").trim().replace("%player_name%", p?.name ?: "")
                server.dispatchCommand(server.consoleSender, cmd)
            }
            action.startsWith("[MESSAGE]") -> {
                p?.sendMessage(prefix + action.substringAfter("[MESSAGE]").trim().replace('&', '§'))
            }
            action.startsWith("[TITLE]") -> {
                val parts = action.substringAfter("[TITLE]").trim().split("|", limit = 2)
                val title = (parts.getOrNull(0) ?: "").replace('&', '§')
                val sub = (parts.getOrNull(1) ?: "").replace('&', '§')
                p?.sendTitle(title, sub, 10, 60, 10)
            }
            action.startsWith("[BROADCAST]") -> {
                val msg = action.substringAfter("[BROADCAST]").trim().replace("%player_name%", p?.name ?: "").replace('&', '§')
                server.broadcastMessage(prefix + msg)
            }
            action.startsWith("[SOUND]") -> {
                val args = action.substringAfter("[SOUND]").trim().split(" ")
                val type = args.getOrNull(0)?.uppercase() ?: return
                val vol = args.getOrNull(1)?.toFloatOrNull() ?: 1.0f
                val pitch = args.getOrNull(2)?.toFloatOrNull() ?: 1.0f
                val sound = runCatching { Sound.valueOf(type) }.getOrNull() ?: return
                p?.playSound(p.location, sound, vol, pitch)
            }
            action.startsWith("[EFFECT]") -> {
                val args = action.substringAfter("[EFFECT]").trim().split(" ")
                val typeName = args.getOrNull(0)?.uppercase() ?: return
                val level = (args.getOrNull(1)?.toIntOrNull() ?: 1).coerceAtLeast(1)
                val seconds = (args.getOrNull(2)?.toIntOrNull() ?: 5).coerceAtLeast(1)
                val type = PotionEffectType.getByName(typeName) ?: return
                p?.addPotionEffect(PotionEffect(type, seconds * 20, level - 1))
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
                    applyNbtSafely(stack, cleanNbt, force, p)
                }
                p?.inventory?.addItem(stack)
            }
            action.startsWith("[POINTS]") -> {
                val spec = action.substringAfter("[POINTS]").trim()
                val value = parsePointsValue(spec)
                val cents = kotlin.math.round(value * 100.0)
                if (p == null) return
                Points.addPoints(p.uniqueId, cents)
            }
        }
    }

    private fun applyNbtSafely(stack: ItemStack, nbt: String, force: Boolean, player: Player?) {
        var processedNbt = nbt.trim()
        if (force) {
            processedNbt = "{${processedNbt.removeSurrounding("{", "}").trim()}}"
        }

        runCatching {
            Bukkit.getUnsafe().modifyItemStack(stack, processedNbt)
        }.onFailure { e ->
            val errorMessage = prefix + "Failed to parse item NBT: ${e.cause?.message ?: e.message}"
            player?.sendMessage(errorMessage)
        }
    }

    private fun parsePointsValue(spec: String): Double {
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
            fmt?.endsWith("f") == true -> String.format("%.${fmt.dropLast(1)}f", value).toDouble()
            else -> value.toDouble()
        }
    }
}
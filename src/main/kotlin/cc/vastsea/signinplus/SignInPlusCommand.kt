package cc.vastsea.signinplus

import cc.vastsea.signinplus.util.PrefixUtil
import cc.vastsea.signinplus.storage.Checkins
import cc.vastsea.signinplus.storage.CorrectionSlips
import cc.vastsea.signinplus.storage.PlayerStat
import cc.vastsea.signinplus.storage.Points
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player
import java.util.*

class SignInPlusCommand(private val plugin: SignInPlus) : CommandExecutor, TabCompleter {
    private val prefix
        get() = PrefixUtil.fromConfig(plugin)

    private fun loc(key: String, placeholders: Map<String, String>? = null): String {
        return SignInPlus.localization.get(key, placeholders)
    }

    private fun onlinePlayerNames(): List<String> = plugin.server.onlinePlayers.map { it.name }
    private fun suggestNumbers(): List<String> = listOf("1", "2", "3", "5", "10")
    private fun formatPointsDisplay(raw: Double): String = String.format("%.2f", raw / 100.0)

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (args.isEmpty()) {
            val alias = label.lowercase()
            if (alias in listOf("signin", "checkin", "qiandao", "qd") && sender is Player) {
                if (!sender.hasPermission("signinplus.user")) {
                    sender.sendMessage("$prefix§c${loc("commands.no_permission")}")
                    return true
                }

                if (Checkins.isSignedIn(sender.uniqueId)) {
                    sender.sendMessage("$prefix§e${loc("commands.already_signed_in")}")
                } else {
                    Checkins.signInToday(sender.uniqueId)
                    plugin.rewardExecutor.onSignedIn(sender.uniqueId)
                    sender.sendMessage("$prefix§a${loc("commands.sign_in_success")}")
                }
                return true
            }
            sendHelp(sender)
            return true
        }

        when (args[0].lowercase()) {
            "force_check_in" -> {
                if (!sender.hasPermission("signinplus.admin")) {
                    sender.sendMessage("$prefix§c${loc("commands.no_permission")}")
                    return true
                }
                if (args.size != 2) {
                    // zh_CN only provides a generic usage key: commands.usage expects {usage}
                    sender.sendMessage("$prefix${loc("commands.usage", mapOf("usage" to "/$label force_check_in <player>"))}")
                    return true
                }
                val target = args[1]
                val player = plugin.server.getPlayerExact(target) ?: return false
                Checkins.signInToday(player.uniqueId)
                plugin.rewardExecutor.onSignedIn(player.uniqueId)
                // zh_CN uses force_sign_in_success with placeholder {target}
                sender.sendMessage("$prefix§a${loc("commands.force_sign_in_success", mapOf("target" to target))}")
            }

            "status" -> {
                if (!sender.hasPermission("signinplus.user")) {
                    sender.sendMessage("$prefix§c${loc("commands.no_permission")}")
                    return true
                }
                val targetName = if (args.size > 1) args[1] else sender.name
                val player = plugin.server.getPlayerExact(targetName) ?: return false
                val stat = PlayerStat(player.uniqueId)
                val signedToday = Checkins.isSignedIn(player.uniqueId)
                val totalDays = Checkins.getTotalDays(player.uniqueId)
                val streakDays = Checkins.getStreakDays(player.uniqueId)
                val missedDays = Checkins.getMissedDays(player.uniqueId)
                val correctionSlips = stat.correctionSlipAmount
                val usableSlips = missedDays.coerceAtMost(correctionSlips)
                //    stats_of: "§b玩家 {targetName} 的签到状态"
                //    status_today: "今日签到: {status}"
                //    total_sign_ins: "累计签到天数: §6{totalDays}"
                //    sign_in_streak: "连续签到天数: §6{streakDays}"
                //    missing_days: "漏签天数: §c{missedDays}"
                //    correction_slips: "剩余补签卡: §6{correctionSlips}"
                //    usable_slips: "当前可使用补签卡: §6{usableSlips}"
                // zh_CN status.stats_of expects {targetName}
                sender.sendMessage("$prefix§b${loc("commands.status.stats_of", mapOf("targetName" to targetName))}")
                sender.sendMessage("$prefix§a${
                    loc(
                        "commands.status.status_today", mapOf(
                            "status" to if (signedToday) {
                                loc("commands.status.signed_in")
                            } else {
                                loc("commands.status.not_signed_in")
                            }
                        )
                    )
                }")
                sender.sendMessage(
                    "$prefix${
                        loc(
                            "commands.status.total_sign_ins",
                            mapOf("totalDays" to totalDays.toString())
                        )
                    }"
                )
                sender.sendMessage(
                    "$prefix${
                        loc(
                            "commands.status.sign_in_streak",
                            mapOf("streakDays" to streakDays.toString())
                        )
                    }"
                )
                sender.sendMessage(
                    "$prefix${
                        loc(
                            "commands.status.missing_days",
                            mapOf("missedDays" to missedDays.toString())
                        )
                    }"
                )
                sender.sendMessage(
                    "$prefix${
                        loc(
                            "commands.status.correction_slips",
                            mapOf("correctionSlips" to correctionSlips.toString())
                        )
                    }"
                )
                sender.sendMessage(
                    "$prefix${
                        loc(
                            "commands.status.usable_slips",
                            mapOf("usableSlips" to usableSlips.toString())
                        )
                    }"
                )
            }

            "help" -> {
                sendHelp(sender)
            }

            "reload" -> {
                if (!sender.hasPermission("signinplus.admin")) {
                    sender.sendMessage("$prefix§c${loc("commands.no_permission")}")
                    return true
                }
                plugin.reloadAll()
                sender.sendMessage("$prefix§a${loc("commands.config_reloaded")}")
            }

            "points" -> {
                if (args.size == 1 && sender is Player) {
                    val p = Points.getPoints(sender.uniqueId)
                    sender.sendMessage(
                        "$prefix${
                            loc(
                                "commands.your_points",
                                mapOf("points" to formatPointsDisplay(p))
                            )
                        }"
                    )
                    return true
                }

                val sub = args[1].lowercase()
                when (sub) {
                    "set" -> {
                        if (!sender.hasPermission("signinplus.admin")) {
                            sender.sendMessage("$prefix§c${loc("commands.no_permission")}")
                            return true
                        }
                        if (args.size >= 4) {
                            val target = args[2]
                            val amount = args[3].toDoubleOrNull()
                            val player = plugin.server.getPlayerExact(target) ?: return false

                            if (amount == null) {
                                // zh_CN uses points_must_be_number
                                sender.sendMessage("$prefix§c${loc("commands.points_must_be_number")}")
                            } else {
                                val cents = kotlin.math.round(amount * 100.0).toDouble()
                                Points.setPoints(player.uniqueId, cents)
                                sender.sendMessage(
                                    "$prefix§a${
                                        loc(
                                            // zh_CN: set_points_success expects {target} and {points}
                                            "commands.set_points_success",
                                            mapOf("target" to target, "points" to String.format("%.2f", amount))
                                        )
                                    }"
                                )
                            }
                        } else sender.sendMessage("$prefix${loc("commands.usage", mapOf("usage" to "/$label points set <player> <amount>"))}")
                    }

                    "clear" -> {
                        if (!sender.hasPermission("signinplus.admin")) {
                            sender.sendMessage("$prefix§c${loc("commands.no_permission")}")
                            return true
                        }
                        if (args.size >= 3) {
                            val target = args[2]
                            val player = plugin.server.getPlayerExact(target) ?: return false
                            Points.setPoints(player.uniqueId, 0.0)
                            // zh_CN: clear_points_success expects {target}
                            sender.sendMessage("$prefix§a${loc("commands.clear_points_success", mapOf("target" to target))}")
                        } else sender.sendMessage("$prefix${loc("commands.usage", mapOf("usage" to "/$label points clear <player>"))}")
                    }

                    "decrease" -> {
                        if (!sender.hasPermission("signinplus.admin")) {
                            sender.sendMessage("$prefix§c${loc("commands.no_permission")}")
                            return true
                        }
                        if (args.size >= 4) {
                            val target = args[2]
                            val amount = args[3].toDoubleOrNull()
                                if (amount == null) {
                                sender.sendMessage("$prefix§c${loc("commands.points_must_be_number")}")
                            } else {
                                val cents = -kotlin.math.round(amount * 100.0)
                                val player = plugin.server.getPlayerExact(target) ?: return false
                                Points.addPoints(player.uniqueId, cents)
                                // zh_CN: decrease_points_success expects {points} and {target}
                                sender.sendMessage("$prefix§a${loc("commands.decrease_points_success", mapOf("target" to target, "points" to String.format("%.2f", amount)))}")
                            }
                        } else sender.sendMessage("$prefix${loc("commands.usage", mapOf("usage" to "/$label points decrease <player> <amount>"))}")
                    }

                    "add" -> {
                        if (!sender.hasPermission("signinplus.admin")) {
                            sender.sendMessage("$prefix§c${loc("commands.no_permission")}")
                            return true
                        }
                        if (args.size >= 4) {
                            val target = args[2]
                            val amount = args[3].toDoubleOrNull()
                            if (amount == null) {
                                sender.sendMessage("$prefix§c${loc("commands.points_must_be_number")}")
                            } else {
                                val cents = kotlin.math.round(amount * 100.0)
                                val player = plugin.server.getPlayerExact(target) ?: return false
                                Points.addPoints(player.uniqueId, cents)
                                // zh_CN: increase_points_success expects {points} and {target}
                                sender.sendMessage("$prefix§a${loc("commands.increase_points_success", mapOf("target" to target, "points" to String.format("%.2f", amount)))}")
                            }
                        } else sender.sendMessage("$prefix${loc("commands.usage", mapOf("usage" to "/$label points add <player> <amount>"))}")
                    }

                    else -> {
                        // 兼容：/points <player> 查看某人积分
                        val target = args[1]
                        val player = plugin.server.getPlayerExact(target) ?: return false
                        val p = Points.getPoints(player.uniqueId)
                        // zh_CN lacks a direct "other player's points" template with amount.
                        // Use the existing some_one_s_point as a title and then show the amount.
                        sender.sendMessage("$prefix${loc("commands.some_one_s_point", mapOf("target" to target))}")
                        sender.sendMessage("$prefix${loc("commands.your_points", mapOf("points" to formatPointsDisplay(p)))}")
                    }
                }
            }

            "correction_slip" -> {
                if (!sender.hasPermission("signinplus.admin")) {
                    sender.sendMessage("$prefix§c${loc("commands.no_permission")}")
                    return true
                }
                if (args.size == 1 && sender is Player) {
                    val stat = PlayerStat(sender.uniqueId)
                    val missedDays = Checkins.getMissedDays(sender.uniqueId)
                    val correctionSlips = stat.correctionSlipAmount
                    val usableSlips = missedDays.coerceAtMost(correctionSlips)
                    // zh_CN provides slips_left ({slips}) and usable_slips ({usableSlips})
                    sender.sendMessage("$prefix${loc("commands.slips_left", mapOf("slips" to correctionSlips.toString()))}")
                    sender.sendMessage("$prefix${loc("commands.usable_slips", mapOf("usableSlips" to usableSlips.toString()))}")
                    return true
                }
                if (args.size >= 2) {
                    when (args[1].lowercase()) {
                        "give" -> {
                            if (args.size >= 3) {
                                val target = args[2]
                                val amount = if (args.size >= 4) args[3].toIntOrNull() ?: 1 else 1
                                val player = plugin.server.getPlayerExact(target) ?: return false
                                CorrectionSlips.giveCorrectionSlip(player.uniqueId, amount)
                                // zh_CN: give_slips_success expects {target} and {amount}
                                sender.sendMessage("$prefix§a${loc("commands.give_slips_success", mapOf("target" to target, "amount" to amount.toString()))}")
                            } else sender.sendMessage("$prefix${loc("commands.usage", mapOf("usage" to "/$label correction_slip give <player> [amount]"))}")
                        }

                        "decrease" -> {
                            if (args.size >= 3) {
                                val target = args[2]
                                val amount = if (args.size >= 4) args[3].toIntOrNull() ?: 1 else 1
                                val player = plugin.server.getPlayerExact(target) ?: return false
                                CorrectionSlips.decreaseCorrectionSlip(player.uniqueId, amount)
                                // zh_CN: decrease_slips_success expects {target} and {amount}
                                sender.sendMessage("$prefix§a${loc("commands.decrease_slips_success", mapOf("target" to target, "amount" to amount.toString()))}")
                            } else sender.sendMessage("$prefix${loc("commands.usage", mapOf("usage" to "/$label correction_slip decrease <player> [amount]"))}")
                        }

                        "clear" -> {
                            if (args.size >= 3) {
                                val target = args[2]
                                val player = plugin.server.getPlayerExact(target) ?: return false
                                CorrectionSlips.clearCorrectionSlip(player.uniqueId)
                                // zh_CN: clear_slips_success expects {target}
                                sender.sendMessage("$prefix§a${loc("commands.clear_slips_success", mapOf("target" to target))}")
                            } else sender.sendMessage("$prefix${loc("commands.usage", mapOf("usage" to "/$label correction_slip clear <player>"))}")
                        }

                        else -> {
                            // No specific subcommands key in zh_CN; use generic usage message
                            sender.sendMessage("$prefix${loc("commands.usage", mapOf("usage" to "/$label correction_slip <give|decrease|clear> ..."))}")
                        }
                    }
                } else sender.sendMessage("$prefix${loc("commands.usage", mapOf("usage" to "/$label correction_slip <give|decrease|clear> ..."))}")
            }

            "make_up" -> {
                if (!sender.hasPermission("signinplus.make_up")) {
                    sender.sendMessage("$prefix§c${loc("commands.no_permission")}")
                    return true
                }

                val isAdmin = sender.hasPermission("signinplus.admin")

                // Parsing for: /make_up [cards] [player] [force]
                var cards = 1
                var target = sender.name
                var force = false

                if (args.size >= 2) {
                    args[1].toIntOrNull()?.let { cards = if (it > 0) it else 1 }
                }

                if (args.size >= 3) {
                    // /make_up <cards> force
                    if (args[2].equals("force", ignoreCase = true)) {
                        force = true
                    } else {
                        // /make_up <cards> <player> [force]
                        target = args[2]
                        if (args.size >= 4 && args[3].equals("force", ignoreCase = true)) {
                            force = true
                        }
                    }
                }

                if (!isAdmin && target != sender.name) {
                    // zh_CN key: can_not_make_up_others
                    sender.sendMessage("$prefix§c${loc("commands.can_not_make_up_others")}")
                    return true
                }

                val player = plugin.server.getPlayerExact(target) ?: return false
                val finalForce = force || isAdmin
                val (madeUpDates, refundedCards) = Checkins.makeUpSign(player.uniqueId, cards, finalForce)

                val today = java.time.LocalDate.now()
                val signedInToday = madeUpDates.contains(today)

                if (signedInToday) {
                    // Temporarily remove today's sign-in to correctly calculate streak and trigger rewards
                    Checkins.getSignedDates(player.uniqueId).toMutableList().remove(today)
                }

                if (madeUpDates.isNotEmpty()) {
                    val pastDaysMadeUp = madeUpDates.filter { it.isBefore(today) }

                    if (pastDaysMadeUp.isNotEmpty()) {
                        // zh_CN provides make_up_success (with embedded count in original template)
                        sender.sendMessage("$prefix§a${loc("commands.make_up_success")}")

                        if (refundedCards > 0) {
                            // zh_CN uses refund_slip_success with {refundedCards}
                            sender.sendMessage("$prefix§e${loc("commands.refund_slip_success", mapOf("refundedCards" to refundedCards.toString()))}")
                        }
                    } else if (signedInToday) {
                        // zh_CN: cannot_make_up_today
                        sender.sendMessage("$prefix§a${loc("commands.cannot_make_up_today")}")
                    }

                    plugin.rewardExecutor.onSignedIn(player.uniqueId)

                    if (signedInToday) {
                        // Add today's sign-in back after rewards have been processed
                        Checkins.getSignedDates(player.uniqueId).toMutableList().add(today)
                    }

                    val totalDays = Checkins.getSignedDates(player.uniqueId).size
                    // zh_CN has streak_days: "当前累计签到天数: {days}"
                    sender.sendMessage("$prefix§a${loc("commands.streak_days", mapOf("days" to totalDays.toString()))}")
                } else {
                    // zh_CN: no_slips
                    sender.sendMessage("$prefix§e${loc("commands.no_slips")}")
                }
            }

            "top" -> {
                val mode = if (args.size >= 2) args[1].lowercase() else "total"
                when (mode) {
                    "streak" -> showTop(sender, loc("commands.top_streak"), Checkins.topStreak(10))
                        else -> showTop(sender, loc("commands.top_total"), Checkins.topTotal(10))
                }
            }

            "debug" -> {
                if (!sender.hasPermission("signinplus.admin")) {
                    sender.sendMessage("$prefix§c${loc("commands.no_permission")}")
                    return true
                }
                if (!plugin.config.getBoolean("debug", false)) {
                    // no specific zh_CN key for debug.disabled; use generic no_permission message
                    sender.sendMessage("$prefix§c${loc("commands.no_permission")}")
                    return true
                }
                if (args.size < 2) {
                    // Use generic usage key
                    sender.sendMessage("$prefix${loc("commands.usage", mapOf("usage" to "/$label debug trigger <type> [value]"))}")
                    return true
                }
                when (args[1].lowercase()) {
                    "trigger" -> handleDebugTrigger(sender, args.drop(2))
                    else -> sender.sendMessage("${prefix}§eUnknown debug command. Usage: /$label debug trigger <type> [value]")
                }
            }

            else -> {
                // Use generic usage when subcommand unknown
                sender.sendMessage("$prefix§e${loc("commands.usage", mapOf("usage" to "/$label <subcommand>"))}")
            }
        }
        return true
    }

    private fun showTop(sender: CommandSender, title: String, ranked: List<Pair<UUID, Int>>) {
        sender.sendMessage("$prefix$title")
        // zh_CN does not define per-line templates for top list; build lines directly
        for (i in 0 until 10) {
            val line = ranked.getOrNull(i)?.let {
                "§e${i + 1}. ${it.first} - ${it.second}"
            } ?: "§7${i + 1}. —"
            sender.sendMessage("$prefix$line")
        }
    }

    private fun sendHelp(sender: CommandSender) {
        // zh_CN does not provide the help.* keys; present a minimal help using existing keys
        sender.sendMessage("$prefix${loc("commands.usage", mapOf("usage" to "/signin | /status | /make_up | /points | /top | /correction_slip"))}")
        sender.sendMessage("$prefix${loc("commands.top_total")}")
        sender.sendMessage("$prefix${loc("commands.top_streak")}")
    }

    private fun handleDebugTrigger(sender: CommandSender, args: List<String>) {
        if (args.isEmpty()) {
            // generic usage
            sender.sendMessage("$prefix§e${loc("commands.usage", mapOf("usage" to "/<label> debug trigger <type> [value]"))}")
            return
        }

        val type = args[0].lowercase()
        val value = args.getOrNull(1)
        val player = sender as? Player ?: run {
            sender.sendMessage("${prefix}§cThis command can only be run by a player.")
            return
        }

    // No specific zh_CN key for triggering; show a generic positive info using config_reloaded as closest available success message
    sender.sendMessage("$prefix§a${loc("commands.config_reloaded")}")

        when (type) {
            "default" -> plugin.rewardExecutor.runDefaultRewards(player.uniqueId)
            "cumulative" -> {
                val days = value?.toIntOrNull()
                if (days == null) {
                    sender.sendMessage("$prefix§e${loc("commands.usage", mapOf("usage" to "/signinplus debug trigger cumulative <days>"))}")
                    return
                }
                val validDays = plugin.config.getMapList("cumulative").mapNotNull { it["times"]?.toString() }
                if (days.toString() !in validDays) {
                    sender.sendMessage(
                        "$prefix§c${
                            loc(
                                "commands.debug.invalid_days",
                                mapOf("options" to validDays.joinToString(" | "))
                            )
                        }"
                    )
                    return
                }
                plugin.rewardExecutor.runCumulativeRewards(days, player.uniqueId, true)
            }

            "streak" -> {
                val days = value?.toIntOrNull()
                if (days == null) {
                    sender.sendMessage("$prefix§e${loc("commands.usage", mapOf("usage" to "/signinplus debug trigger streak <days>"))}")
                    return
                }
                val validDays = plugin.config.getMapList("streak").mapNotNull { it["times"]?.toString() }
                if (days.toString() !in validDays) {
                    sender.sendMessage(
                        "$prefix§c${
                            loc(
                                "commands.debug.invalid_days",
                                mapOf("options" to validDays.joinToString(" | "))
                            )
                        }"
                    )
                    return
                }
                plugin.rewardExecutor.runStreakRewards(days, player.uniqueId, true)
            }

            "top" -> {
                val rank = value?.toIntOrNull()
                if (rank == null) {
                    val ranks =
                        plugin.config.getMapList("top").mapNotNull { it["rank"]?.toString() }.joinToString(" | ")
                    sender.sendMessage("$prefix§e${loc("commands.usage", mapOf("usage" to "/signinplus debug trigger top <rank>"))}")
                    // show available ranks as plain text (no zh_CN key for available_ranks)
                    sender.sendMessage("$prefix§7$ranks")
                    return
                }
                val validRanks = plugin.config.getMapList("top").mapNotNull { it["rank"]?.toString() }
                if (rank.toString() !in validRanks) {
                    sender.sendMessage(
                        "$prefix§c${
                            loc(
                                "commands.debug.invalid_rank",
                                mapOf("options" to validRanks.joinToString(" | "))
                            )
                        }"
                    )
                    return
                }
                plugin.rewardExecutor.runTopRewards(rank, player.uniqueId, true)
            }

            "special_dates" -> {
                if (value == null) {
                    val dates = plugin.config.getMapList("special_dates").mapNotNull { it["date"]?.toString() }
                        .joinToString(" | ")
                    sender.sendMessage("$prefix§e${loc("commands.usage", mapOf("usage" to "/signinplus debug trigger special_dates <date> [prev]"))}")
                    // show available dates as plain text
                    sender.sendMessage("$prefix§7$dates")
                    return
                }
                val validDates = plugin.config.getMapList("special_dates").mapNotNull { it["date"]?.toString() }
                if (value !in validDates) {
                    sender.sendMessage("${prefix}§cInvalid date value. Available options: ${validDates.joinToString(" | ")}")
                    return
                }

                val entry = plugin.config.getMapList("special_dates")
                    .find { (it["date"] as? String)?.equals(value, true) == true }
                val repeatEnabled = (entry?.get("repeat") as? Boolean) ?: false
                val limit = ((entry?.get("repeat_time") as? Number)?.toInt() ?: 1).coerceAtLeast(0)

                val prevArg = args.getOrNull(2)
                if (prevArg != null) {
                    if (!repeatEnabled) {
                        sender.sendMessage("$prefix§c${loc("commands.no_permission")}")
                        return
                    }
                    val prev = prevArg.toIntOrNull()
                    if (prev == null) {
                        sender.sendMessage("$prefix§e${loc("commands.usage", mapOf("usage" to "/signinplus debug trigger special_dates <date> [prev]"))}")
                        sender.sendMessage("$prefix§7${loc("commands.usage", mapOf("usage" to "previous day must be integer 0..$limit"))}")
                        return
                    }
                    if (prev < 0 || prev > limit) {
                        sender.sendMessage("$prefix§c${loc("commands.usage", mapOf("usage" to "prev must be 0..$limit"))}")
                        return
                    }
                    plugin.rewardExecutor.runSpecialDateRewards(value, player.uniqueId, prev)
                    sender.sendMessage("$prefix§a${loc("commands.config_reloaded")}")
                    return
                }

                plugin.rewardExecutor.runSpecialDateRewards(value, player.uniqueId, null)
            }

            else -> {
                sender.sendMessage("$prefix§e${loc("commands.usage", mapOf("usage" to "Unknown debug trigger: $type"))}")
                return
            }
        }

        // Use a generic positive message (closest existing key)
        sender.sendMessage("$prefix§a${loc("commands.config_reloaded")}")
    }

    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<out String>
    ): MutableList<String> {
        val out = mutableListOf<String>()
        if (args.size == 1) {
            val base = listOf(
                "help",
                "reload",
                "force_check_in",
                "status",
                "make_up",
                "correction_slip",
                "points",
                "top",
                "debug"
            )
            out.addAll(base.filter { it.startsWith(args[0], ignoreCase = true) })
            return out
        }

        when (args[0].lowercase()) {
            "top" -> {
                if (args.size == 2) out.addAll(listOf("total", "streak").filter {
                    it.startsWith(
                        args[1],
                        ignoreCase = true
                    )
                })
            }

            "debug" -> {
                if (args.size == 2) {
                    out.addAll(listOf("trigger").filter { it.startsWith(args[1], ignoreCase = true) })
                }
                if (args.size == 3) {
                    out.addAll(listOf("default", "cumulative", "streak", "top", "special_dates").filter {
                        it.startsWith(
                            args[2],
                            ignoreCase = true
                        )
                    })
                }
                if (args.size == 4) {
                    when (args[2].lowercase()) {
                        "top" -> {
                            val ranks = plugin.config.getMapList("top").mapNotNull { it["rank"]?.toString() }
                            out.addAll(ranks.filter { it.startsWith(args[3], ignoreCase = true) })
                        }

                        "special_dates" -> {
                            val dates = plugin.config.getMapList("special_dates").mapNotNull { it["date"]?.toString() }
                            out.addAll(dates.filter { it.startsWith(args[3], ignoreCase = true) })
                        }

                        "cumulative" -> {
                            val times = plugin.config.getMapList("cumulative").mapNotNull { it["times"]?.toString() }
                            out.addAll(times.filter { it.startsWith(args[3], ignoreCase = true) })
                        }

                        "streak" -> {
                            val times = plugin.config.getMapList("streak").mapNotNull { it["times"]?.toString() }
                            out.addAll(times.filter { it.startsWith(args[3], ignoreCase = true) })
                        }
                    }
                }
                // Provide the optional [previous_day] only when repeat=true on the chosen special date
                if (args.size == 5 && args[2].equals("special_dates", ignoreCase = true)) {
                    val chosenDate = args[3]
                    val entry = plugin.config.getMapList("special_dates")
                        .find { (it["date"] as? String)?.equals(chosenDate, true) == true }
                    val repeatEnabled = (entry?.get("repeat") as? Boolean) ?: false
                    if (repeatEnabled) {
                        val limit = ((entry?.get("repeat_time") as? Number)?.toInt() ?: 1).coerceAtLeast(0)
                        val options = (0..limit).map { it.toString() }
                        out.addAll(options.filter { it.startsWith(args[4], ignoreCase = true) })
                    }
                }
            }

            "points" -> {
                if (args.size == 2) {
                    val subs = listOf("set", "decrease", "clear", "add")
                    out.addAll(subs.filter { it.startsWith(args[1], ignoreCase = true) })
                    out.addAll(onlinePlayerNames().filter { it.startsWith(args[1], ignoreCase = true) })
                } else if (args.size == 3) {
                    when (args[1].lowercase()) {
                        "set", "decrease", "add" -> out.addAll(onlinePlayerNames().filter {
                            it.startsWith(
                                args[2],
                                ignoreCase = true
                            )
                        })

                        "clear" -> out.addAll(onlinePlayerNames().filter { it.startsWith(args[2], ignoreCase = true) })
                    }
                } else if (args.size == 4) {
                    when (args[1].lowercase()) {
                        "set", "decrease", "add" -> out.addAll(suggestNumbers().filter {
                            it.startsWith(
                                args[3],
                                ignoreCase = true
                            )
                        })
                    }
                }
            }

            "correction_slip" -> {
                when (args.size) {
                    2 -> out.addAll(listOf("give", "decrease", "clear").filter {
                        it.startsWith(
                            args[1],
                            ignoreCase = true
                        )
                    })

                    3 -> out.addAll(onlinePlayerNames().filter { it.startsWith(args[2], ignoreCase = true) })
                    4 -> out.addAll(suggestNumbers().filter { it.startsWith(args[3], ignoreCase = true) })
                }
            }

            "make_up" -> {
                when (args.size) {
                    2 -> out.addAll(suggestNumbers().filter { it.startsWith(args[1], ignoreCase = true) })
                    3 -> {
                        out.addAll(onlinePlayerNames().filter { it.startsWith(args[2], ignoreCase = true) })
                        out.add("force")
                    }

                    4 -> {
                        if (args[2].equals("force", ignoreCase = true)) {
                            // No suggestions after force
                        } else {
                            out.add("force")
                        }
                    }
                }
            }

            "force_check_in" -> {
                if (args.size == 2) out.addAll(onlinePlayerNames().filter { it.startsWith(args[1], ignoreCase = true) })
            }
        }

        return out
    }
}
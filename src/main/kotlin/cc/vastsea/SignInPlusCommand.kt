package cc.vastsea

import cc.vastsea.storage.Checkins
import cc.vastsea.storage.CorrectionSlips
import cc.vastsea.storage.PlayerStat
import cc.vastsea.storage.Points

import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player
import java.util.UUID

class SignInPlusCommand(private val plugin: SignInPlus) : CommandExecutor, TabCompleter {
    private val prefix
        get() = (plugin.config.getString("message_prefix") ?: "&7[&a签到Plus&7] ").replace('&', '§')

    private fun onlinePlayerNames(): List<String> = plugin.server.onlinePlayers.map { it.name }
    private fun suggestNumbers(): List<String> = listOf("1", "2", "3", "5", "10")
    private fun formatPointsDisplay(raw: Double): String = String.format("%.2f", raw / 100.0)

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (args.isEmpty()) {
            val alias = label.lowercase()
            if (alias in listOf("signin", "checkin", "qiandao", "qd") && sender is Player) {
                if (!sender.hasPermission("signinplus.user")) {
                    sender.sendMessage("${prefix}§c你没有权限执行此命令！")
                    return true
                }

                if (Checkins.isSignedIn(sender.uniqueId)) {
                    sender.sendMessage("${prefix}§e你今天已经签到过了！")
                } else {
                    Checkins.signInToday(sender.uniqueId)
                    plugin.rewardExecutor.onSignedIn(sender.uniqueId)
                    sender.sendMessage("${prefix}§a签到成功！")
                }
                return true
            }
            sendHelp(sender)
            return true
        }

        when (args[0].lowercase()) {
            "force_check_in" -> {
                if (!sender.hasPermission("signinplus.admin")) {
                    sender.sendMessage("${prefix}§c你没有权限执行此命令！")
                    return true
                }
                if (args.size != 2) {
                    sender.sendMessage("${prefix}§e用法: /$label force_check_in <player>")
                    return true
                }
                val target = args[1]
                val player = plugin.server.getPlayerExact(target) ?: return false
                Checkins.signInToday(player.uniqueId)
                plugin.rewardExecutor.onSignedIn(player.uniqueId)
                sender.sendMessage("${prefix}§a已为 $target 强制签到今日")
            }

            "status" -> {
                if (!sender.hasPermission("signinplus.user")) {
                    sender.sendMessage("${prefix}§c你没有权限执行此命令！")
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

                sender.sendMessage("${prefix}§b玩家 $targetName 的签到状态")
                sender.sendMessage("今日签到: ${if (signedToday) "§a已签到" else "§e未签到"}")
                sender.sendMessage("总签到天数: §6$totalDays")
                sender.sendMessage("连续签到天数: §6$streakDays")
                sender.sendMessage("漏签天数: §c$missedDays")
                sender.sendMessage("剩余补签卡: §6$correctionSlips")
                sender.sendMessage("当前可使用补签卡: §6$usableSlips")
            }

            "help" -> {
                sendHelp(sender)
            }

            "reload" -> {
                if (!sender.hasPermission("signinplus.admin")) {
                    sender.sendMessage("${prefix}§c你没有权限执行此命令！")
                    return true
                }
                plugin.reloadAll()
                sender.sendMessage("${prefix}§a配置已重载并重启 Web API（如启用）")
            }

            "points" -> {
                if (args.size == 1 && sender is Player) {
                    val p = Points.getPoints(sender.uniqueId)
                    sender.sendMessage("${prefix}你的积分: ${formatPointsDisplay(p)}")
                    return true
                }

                val sub = args[1].lowercase()
                when (sub) {
                    "set" -> {
                        if (!sender.hasPermission("signinplus.admin")) {
                            sender.sendMessage("${prefix}§c你没有权限执行此命令！")
                            return true
                        }
                        if (args.size >= 4) {
                            val target = args[2]
                            val amount = args[3].toDoubleOrNull()
                            val player = plugin.server.getPlayerExact(target) ?: return false

                            if (amount == null) {
                                sender.sendMessage("${prefix}§c积分必须是数字，例如 1 或 1.25")
                            } else {
                                val cents = kotlin.math.round(amount * 100.0).toDouble()
                                Points.setPoints(player.uniqueId, cents)
                                sender.sendMessage(
                                    "${prefix}§a已设置 $target 的积分为 ${
                                        String.format(
                                            "%.2f",
                                            amount
                                        )
                                    }"
                                )
                            }
                        } else sender.sendMessage("${prefix}§e用法: /$label points set <player> <amount>")
                    }

                    "clear" -> {
                        if (!sender.hasPermission("signinplus.admin")) {
                            sender.sendMessage("${prefix}§c你没有权限执行此命令！")
                            return true
                        }
                        if (args.size >= 3) {
                            val target = args[2]
                            val player = plugin.server.getPlayerExact(target) ?: return false
                            Points.setPoints(player.uniqueId, 0.0)
                            sender.sendMessage("${prefix}§a已清零 $target 的积分")
                        } else sender.sendMessage("${prefix}§e用法: /$label points clear <player>")
                    }

                    "decrease" -> {
                        if (!sender.hasPermission("signinplus.admin")) {
                            sender.sendMessage("${prefix}§c你没有权限执行此命令！")
                            return true
                        }
                        if (args.size >= 4) {
                            val target = args[2]
                            val amount = args[3].toDoubleOrNull()
                            if (amount == null) {
                                sender.sendMessage("${prefix}§c积分必须是数字，例如 1 或 1.25")
                            } else {
                                val cents = -kotlin.math.round(amount * 100.0)
                                val player = plugin.server.getPlayerExact(target) ?: return false
                                Points.addPoints(player.uniqueId, cents)
                                sender.sendMessage(
                                    "${prefix}§a已减少 $target 的积分 ${
                                        String.format(
                                            "%.2f",
                                            amount
                                        )
                                    }"
                                )
                            }
                        } else sender.sendMessage("${prefix}§e用法: /$label points decrease <player> <amount>")
                    }

                    else -> {
                        // 兼容：/points <player> 查看某人积分
                        val target = args[1]
                        val player = plugin.server.getPlayerExact(target) ?: return false
                        val p = Points.getPoints(player.uniqueId)
                        sender.sendMessage("${prefix}$target 的积分: ${formatPointsDisplay(p)}")
                    }
                }
            }

            "correction_slip" -> {
                if (!sender.hasPermission("signinplus.admin")) {
                    sender.sendMessage("${prefix}§c你没有权限执行此命令！")
                    return true
                }
                if (args.size == 1 && sender is Player) {
                    val stat = PlayerStat(sender.uniqueId)
                    val missedDays = Checkins.getMissedDays(sender.uniqueId)
                    val correctionSlips = stat.correctionSlipAmount
                    val usableSlips = missedDays.coerceAtMost(correctionSlips)
                    sender.sendMessage("${prefix}剩余补签卡: §6$correctionSlips")
                    sender.sendMessage("${prefix}当前可使用补签卡: §6$usableSlips")
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
                                sender.sendMessage("${prefix}§a已给予 $target 补签卡 $amount 张")
                            } else sender.sendMessage("${prefix}§e用法: /$label correction_slip give <player> [amount]")
                        }

                        "decrease" -> {
                            if (args.size >= 3) {
                                val target = args[2]
                                val amount = if (args.size >= 4) args[3].toIntOrNull() ?: 1 else 1
                                val player = plugin.server.getPlayerExact(target) ?: return false
                                CorrectionSlips.decreaseCorrectionSlip(player.uniqueId, amount)
                                sender.sendMessage("${prefix}§a已减少 $target 补签卡 $amount 张")
                            } else sender.sendMessage("${prefix}§e用法: /$label correction_slip decrease <player> [amount]")
                        }

                        "clear" -> {
                            if (args.size >= 3) {
                                val target = args[2]
                                val player = plugin.server.getPlayerExact(target) ?: return false
                                CorrectionSlips.clearCorrectionSlip(player.uniqueId)
                                sender.sendMessage("${prefix}§a已清除 $target 的补签卡")
                            } else sender.sendMessage("${prefix}§e用法: /$label correction_slip clear <player>")
                        }

                        else -> sender.sendMessage("${prefix}§e子命令: give|decrease|clear")
                    }
                } else sender.sendMessage("${prefix}§e用法: /$label correction_slip <give|decrease|clear> ...")
            }

            "make_up" -> {
                if (!sender.hasPermission("signinplus.make_up")) {
                    sender.sendMessage("${prefix}§c你没有权限执行此命令！")
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
                    sender.sendMessage("${prefix}§c你没有权限为他人补签！")
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
                        var msg = "${prefix}§a已为您补签 ${pastDaysMadeUp.size} 次"
                        if (signedInToday) {
                            msg += "，且为今日签到了。"
                        } else {
                            msg += "。"
                        }
                        sender.sendMessage(msg)

                        if (refundedCards > 0) {
                            sender.sendMessage("${prefix}§e退回 $refundedCards 张补签卡。")
                        }
                    } else if (signedInToday) {
                        sender.sendMessage("${prefix}§a没有可补签的，已为您今日签到。")
                    }

                    plugin.rewardExecutor.onSignedIn(player.uniqueId)

                    if (signedInToday) {
                        // Add today's sign-in back after rewards have been processed
                        Checkins.getSignedDates(player.uniqueId).toMutableList().add(today)
                    }

                    val totalDays = Checkins.getSignedDates(player.uniqueId).size
                    sender.sendMessage("${prefix}§a当前累计签到天数: $totalDays")
                } else {
                    sender.sendMessage("${prefix}§e没有可补签的天数或补签卡不足。")
                }
            }

            "top" -> {
                    val mode = if (args.size >= 2) args[1].lowercase() else "total"
                    when (mode) {
                        "streak" -> showTop(sender, "连续签到天数前十：", Checkins.topStreak(10))
                        else -> showTop(sender, "总天数排行前十：", Checkins.topTotal(10))
                    }
            }

            "debug" -> {
                if (!sender.hasPermission("signinplus.admin")) {
                    sender.sendMessage("${prefix}§cYou do not have permission to use this command.")
                    return true
                }
                if (!plugin.config.getBoolean("debug", false)) {
                    sender.sendMessage("${prefix}§cDebug mode is not enabled in config.yml.")
                    return true
                }
                if (args.size < 2) {
                    sender.sendMessage("${prefix}§eUsage: /$label debug trigger <type> [value]")
                    return true
                }
                when (args[1].lowercase()) {
                    "trigger" -> handleDebugTrigger(sender, args.drop(2))
                    else -> sender.sendMessage("${prefix}§eUnknown debug command. Usage: /$label debug trigger <type> [value]")
                }
            }

            else -> {
                sender.sendMessage("${prefix}§e未知子命令，使用 /$label help 查看帮助")
            }
        }
        return true
    }

    private fun showTop(sender: CommandSender, title: String, ranked: List<Pair<UUID, Int>>) {
        sender.sendMessage("${prefix}$title")
        for (i in 0 until 10) {
            val line = ranked.getOrNull(i)?.let { "${i + 1}. ${it.first} - ${it.second}" } ?: "${i + 1}. -"
            sender.sendMessage(line)
        }
    }

    private fun sendHelp(sender: CommandSender) {
        sender.sendMessage("${prefix}§bSignInPlus 指令帮助")
        sender.sendMessage("§7/signin §f- 直接签到（同 /checkin、/qiandao、/qd）")
        sender.sendMessage("§7/signinplus help §f- 查看帮助")
        sender.sendMessage("§7/signinplus top [total|streak] §f- 查看排行前十（默认 total）")
        sender.sendMessage("§7/signinplus force_check_in <player> §f- 强制为某人签到今日")
        sender.sendMessage("§7/signinplus reload §f- 重载配置并重启 Web API")
     }

     private fun handleDebugTrigger(sender: CommandSender, args: List<String>) {
         if (args.isEmpty()) {
             sender.sendMessage("${prefix}§eUsage: /signinplus debug trigger <type> [value]")
             return
         }

         val type = args[0].lowercase()
         val value = args.getOrNull(1)
         val player = sender as? Player ?: run {
             sender.sendMessage("${prefix}§cThis command can only be run by a player.")
             return
         }

         sender.sendMessage("${prefix}§aTriggering debug reward for type '$type'...")

         when (type) {
             "default" -> plugin.rewardExecutor.runDefaultRewards(player.uniqueId)
             "cumulative" -> {
                 val days = value?.toIntOrNull()
                 if (days == null) {
                     sender.sendMessage("${prefix}§eUsage: /signinplus debug trigger cumulative <days>")
                     return
                 }
                 val validDays = plugin.config.getMapList("cumulative").mapNotNull { it["times"]?.toString() }
                 if (days.toString() !in validDays) {
                     sender.sendMessage("${prefix}§cInvalid days value. Available options: ${validDays.joinToString(" | ")}")
                     return
                 }
                 plugin.rewardExecutor.runCumulativeRewards(days, player.uniqueId, true)
             }

             "streak" -> {
                 val days = value?.toIntOrNull()
                 if (days == null) {
                     sender.sendMessage("${prefix}§eUsage: /signinplus debug trigger streak <days>")
                     return
                 }
                 val validDays = plugin.config.getMapList("streak").mapNotNull { it["times"]?.toString() }
                 if (days.toString() !in validDays) {
                     sender.sendMessage("${prefix}§cInvalid days value. Available options: ${validDays.joinToString(" | ")}")
                     return
                 }
                 plugin.rewardExecutor.runStreakRewards(days, player.uniqueId, true)
             }

             "top" -> {
                 val rank = value?.toIntOrNull()
                 if (rank == null) {
                     val ranks = plugin.config.getMapList("top").mapNotNull { it["rank"]?.toString() }.joinToString(" | ")
                     sender.sendMessage("${prefix}§eUsage: /signinplus debug trigger top <rank>")
                     sender.sendMessage("${prefix}§7Available ranks: $ranks")
                     return
                 }
                 val validRanks = plugin.config.getMapList("top").mapNotNull { it["rank"]?.toString() }
                 if (rank.toString() !in validRanks) {
                     sender.sendMessage("${prefix}§cInvalid rank value. Available options: ${validRanks.joinToString(" | ")}")
                     return
                 }
                 plugin.rewardExecutor.runTopRewards(rank, player.uniqueId, true)
             }

             "special_dates" -> {
                 if (value == null) {
                     val dates = plugin.config.getMapList("special_dates").mapNotNull { it["date"]?.toString() }.joinToString(" | ")
                     sender.sendMessage("${prefix}§eUsage: /signinplus debug trigger special_dates <date> [previous_day]")
                     sender.sendMessage("${prefix}§7Available dates: $dates")
                     return
                 }
                 val validDates = plugin.config.getMapList("special_dates").mapNotNull { it["date"]?.toString() }
                 if (value !in validDates) {
                     sender.sendMessage("${prefix}§cInvalid date value. Available options: ${validDates.joinToString(" | ")}")
                     return
                 }

                 val entry = plugin.config.getMapList("special_dates").find { (it["date"] as? String)?.equals(value, true) == true }
                 val repeatEnabled = (entry?.get("repeat") as? Boolean) ?: false
                 val limit = ((entry?.get("repeat_time") as? Number)?.toInt() ?: 1).coerceAtLeast(0)

                 val prevArg = args.getOrNull(2)
                 if (prevArg != null) {
                     if (!repeatEnabled) {
                         sender.sendMessage("${prefix}§cRepeat is disabled for this rule; previous_day is not accepted.")
                         return
                     }
                     val prev = prevArg.toIntOrNull()
                     if (prev == null) {
                         sender.sendMessage("${prefix}§eUsage: /signinplus debug trigger special_dates <date> [previous_day]")
                         sender.sendMessage("${prefix}§7previous_day must be an integer. Allowed range: 0..$limit")
                         return
                     }
                     if (prev < 0 || prev > limit) {
                         sender.sendMessage("${prefix}§cInvalid previous_day: $prev. Allowed range: 0..$limit")
                         return
                     }
                     plugin.rewardExecutor.runSpecialDateRewards(value, player.uniqueId, prev)
                     sender.sendMessage("${prefix}§aDebug trigger for 'special_dates' executed for ${player.name}.")
                     return
                 }

                 plugin.rewardExecutor.runSpecialDateRewards(value, player.uniqueId, null)
             }

             else -> {
                 sender.sendMessage("${prefix}§eUnknown trigger type: $type")
                 return
             }
         }

         sender.sendMessage("${prefix}§aDebug trigger for '$type' executed for ${player.name}.")
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
                     val entry = plugin.config.getMapList("special_dates").find { (it["date"] as? String)?.equals(chosenDate, true) == true }
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
                     val subs = listOf("set", "decrease", "clear")
                     out.addAll(subs.filter { it.startsWith(args[1], ignoreCase = true) })
                     out.addAll(onlinePlayerNames().filter { it.startsWith(args[1], ignoreCase = true) })
                 } else if (args.size == 3) {
                     when (args[1].lowercase()) {
                         "set", "decrease" -> out.addAll(onlinePlayerNames().filter {
                             it.startsWith(
                                 args[2],
                                 ignoreCase = true
                             )
                         })

                         "clear" -> out.addAll(onlinePlayerNames().filter { it.startsWith(args[2], ignoreCase = true) })
                     }
                 } else if (args.size == 4) {
                     when (args[1].lowercase()) {
                         "set", "decrease" -> out.addAll(suggestNumbers().filter {
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
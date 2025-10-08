package cc.vastsea

import org.bukkit.ChatColor
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter

class SignInPlusCommand(private val plugin: SignInPlus) : CommandExecutor, TabCompleter {
    private val prefix get() = ChatColor.translateAlternateColorCodes('&', plugin.config.getString("message_prefix") ?: "&7[&a签到Plus&7] ")
    private fun onlinePlayerNames(): List<String> = plugin.server.onlinePlayers.map { it.name }
    private fun suggestNumbers(): List<String> = listOf("1", "2", "3", "5", "10")
    private fun formatPointsDisplay(raw: Double): String = String.format("%.2f", raw / 100.0)

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (args.isEmpty()) {
            val alias = label.lowercase()
            if (alias in listOf("signin", "checkin", "qiandao", "qd")) {
                if (!sender.hasPermission("signinplus.user")) {
                    sender.sendMessage("${prefix}${ChatColor.RED}你没有权限执行此命令！")
                    return true
                }
                if (plugin.storage is cc.vastsea.storage.SqliteStorage) {
                    val s = (plugin.storage as cc.vastsea.storage.SqliteStorage)
                    val name = sender.name
                    if (s.isSignedIn(name)) {
                        sender.sendMessage("${prefix}${ChatColor.YELLOW}你今天已经签到过了！")
                    } else {
                        s.signInToday(name)
                        plugin.rewardExecutor.onSignedIn(name)
                        sender.sendMessage("${prefix}${ChatColor.GREEN}签到成功！")
                    }
                } else {
                    sender.sendMessage("${prefix}${ChatColor.RED}当前存储不支持签到操作")
                }
                return true
            }
            sendHelp(sender)
            return true
        }

        when (args[0].lowercase()) {
            "force_check_in" -> {
                if (!sender.hasPermission("signinplus.admin")) {
                    sender.sendMessage("${prefix}${ChatColor.RED}你没有权限执行此命令！")
                    return true
                }
                if (args.size != 2) {
                    sender.sendMessage("${prefix}${ChatColor.YELLOW}用法: /$label force_check_in <player>")
                    return true
                }
                val target = args[1]
                if (plugin.storage is cc.vastsea.storage.SqliteStorage) {
                    (plugin.storage as cc.vastsea.storage.SqliteStorage).signInToday(target)
                    plugin.rewardExecutor.onSignedIn(target)
                    sender.sendMessage("${prefix}${ChatColor.GREEN}已为 $target 强制签到今日")
                } else {
                    sender.sendMessage("${prefix}${ChatColor.RED}当前存储不支持强制签到操作")
                }
            }
            "status" -> {
                if (!sender.hasPermission("signinplus.user")) {
                    sender.sendMessage("${prefix}${ChatColor.RED}你没有权限执行此命令！")
                    return true
                }
                val targetName = if (args.size > 1) args[1] else sender.name
                if (plugin.storage is cc.vastsea.storage.SqliteStorage) {
                    val s = plugin.storage as cc.vastsea.storage.SqliteStorage
                    val stat = s.getInfo(targetName)
                    val signedToday = s.isSignedIn(targetName)
                    val totalDays = s.getTotalDays(targetName)
                    val streakDays = s.getStreakDays(targetName)
                    val missedDays = s.getMissedDays(targetName)
                    val correctionSlips = stat.correctionSlipAmount
                    val usableSlips = missedDays.coerceAtMost(correctionSlips)

                    sender.sendMessage("${prefix}${ChatColor.AQUA}玩家 ${targetName} 的签到状态")
                    sender.sendMessage("今日签到: ${if (signedToday) "${ChatColor.GREEN}已签到" else "${ChatColor.YELLOW}未签到"}")
                    sender.sendMessage("总签到天数: ${ChatColor.GOLD}$totalDays")
                    sender.sendMessage("连续签到天数: ${ChatColor.GOLD}$streakDays")
                    sender.sendMessage("漏签天数: ${ChatColor.RED}$missedDays")
                    sender.sendMessage("剩余补签卡: ${ChatColor.GOLD}$correctionSlips")
                    sender.sendMessage("当前可使用补签卡: ${ChatColor.GOLD}$usableSlips")
                } else {
                    sender.sendMessage("${prefix}${ChatColor.RED}当前存储不支持查看状态")
                }
            }
            "help" -> {
                sendHelp(sender)
            }
            "reload" -> {
                if (!sender.hasPermission("signinplus.admin")) {
                    sender.sendMessage("${prefix}${ChatColor.RED}你没有权限执行此命令！")
                    return true
                }
                plugin.reloadAll()
                sender.sendMessage("${prefix}${ChatColor.GREEN}配置已重载并重启 Web API（如启用）")
            }
            "points" -> {
                if (args.size == 1) {
                    val p = plugin.storage.getPoints(sender.name)
                    sender.sendMessage("${prefix}你的积分: ${formatPointsDisplay(p)}")
                    return true
                }

                val sub = args[1].lowercase()
                when (sub) {
                    "set" -> {
                        if (!sender.hasPermission("signinplus.admin")) {
                            sender.sendMessage("${prefix}${ChatColor.RED}你没有权限执行此命令！")
                            return true
                        }
                        if (plugin.storage is cc.vastsea.storage.SqliteStorage) {
                            if (args.size >= 4) {
                                val target = args[2]
                                val amount = args[3].toDoubleOrNull()
                                if (amount == null) {
                                    sender.sendMessage("${prefix}${ChatColor.RED}积分必须是数字，例如 1 或 1.25")
                                } else {
                                    val cents = kotlin.math.round(amount * 100.0).toDouble()
                                    (plugin.storage as cc.vastsea.storage.SqliteStorage).setPoints(target, cents)
                                    sender.sendMessage("${prefix}${ChatColor.GREEN}已设置 $target 的积分为 ${String.format("%.2f", amount)}")
                                }
                            } else sender.sendMessage("${prefix}${ChatColor.YELLOW}用法: /$label points set <player> <amount>")
                        } else sender.sendMessage("${prefix}${ChatColor.RED}当前存储不支持设置积分")
                    }
                    "clear" -> {
                        if (!sender.hasPermission("signinplus.admin")) {
                            sender.sendMessage("${prefix}${ChatColor.RED}你没有权限执行此命令！")
                            return true
                        }
                        if (plugin.storage is cc.vastsea.storage.SqliteStorage) {
                            if (args.size >= 3) {
                                val target = args[2]
                                (plugin.storage as cc.vastsea.storage.SqliteStorage).setPoints(target, 0.0)
                                sender.sendMessage("${prefix}${ChatColor.GREEN}已清零 $target 的积分")
                            } else sender.sendMessage("${prefix}${ChatColor.YELLOW}用法: /$label points clear <player>")
                        } else sender.sendMessage("${prefix}${ChatColor.RED}当前存储不支持设置积分")
                    }
                    "decrease" -> {
                        if (!sender.hasPermission("signinplus.admin")) {
                            sender.sendMessage("${prefix}${ChatColor.RED}你没有权限执行此命令！")
                            return true
                        }
                        if (plugin.storage is cc.vastsea.storage.SqliteStorage) {
                            if (args.size >= 4) {
                                val target = args[2]
                                val amount = args[3].toDoubleOrNull()
                                if (amount == null) {
                                    sender.sendMessage("${prefix}${ChatColor.RED}积分必须是数字，例如 1 或 1.25")
                                } else {
                                    val cents = -kotlin.math.round(amount * 100.0).toDouble()
                                    (plugin.storage as cc.vastsea.storage.SqliteStorage).addPoints(target, cents)
                                    sender.sendMessage("${prefix}${ChatColor.GREEN}已减少 $target 的积分 ${String.format("%.2f", amount)}")
                                }
                            } else sender.sendMessage("${prefix}${ChatColor.YELLOW}用法: /$label points decrease <player> <amount>")
                        } else sender.sendMessage("${prefix}${ChatColor.RED}当前存储不支持设置积分")
                    }
                    else -> {
                        // 兼容：/points <player> 查看某人积分
                        val target = args[1]
                        val p = plugin.storage.getPoints(target)
                        sender.sendMessage("${prefix}$target 的积分: ${formatPointsDisplay(p)}")
                    }
                }
            }
            "correction_slip" -> {
                if (!sender.hasPermission("signinplus.admin")) {
                    sender.sendMessage("${prefix}${ChatColor.RED}你没有权限执行此命令！")
                    return true
                }
                if (plugin.storage is cc.vastsea.storage.SqliteStorage) {
                    val s = (plugin.storage as cc.vastsea.storage.SqliteStorage)
                    if (args.size == 1) {
                        val stat = s.getInfo(sender.name)
                        val missedDays = s.getMissedDays(sender.name)
                        val correctionSlips = stat.correctionSlipAmount
                        val usableSlips = missedDays.coerceAtMost(correctionSlips)
                        sender.sendMessage("${prefix}剩余补签卡: ${ChatColor.GOLD}$correctionSlips")
                        sender.sendMessage("${prefix}当前可使用补签卡: ${ChatColor.GOLD}$usableSlips")
                        return true
                    }
                    if (args.size >= 2) {
                        when (args[1].lowercase()) {
                            "give" -> {
                                if (args.size >= 3) {
                                    val target = args[2]
                                    val amount = if (args.size >= 4) args[3].toIntOrNull() ?: 1 else 1
                                    s.giveCorrectionSlip(target, amount)
                                    sender.sendMessage("${prefix}${ChatColor.GREEN}已给予 $target 补签卡 $amount 张")
                                } else sender.sendMessage("${prefix}${ChatColor.YELLOW}用法: /$label correction_slip give <player> [amount]")
                            }
                            "decrease" -> {
                                if (args.size >= 3) {
                                    val target = args[2]
                                    val amount = if (args.size >= 4) args[3].toIntOrNull() ?: 1 else 1
                                    s.decreaseCorrectionSlip(target, amount)
                                    sender.sendMessage("${prefix}${ChatColor.GREEN}已减少 $target 补签卡 $amount 张")
                                } else sender.sendMessage("${prefix}${ChatColor.YELLOW}用法: /$label correction_slip decrease <player> [amount]")
                            }
                            "clear" -> {
                                if (args.size >= 3) {
                                    val target = args[2]
                                    s.clearCorrectionSlip(target)
                                    sender.sendMessage("${prefix}${ChatColor.GREEN}已清除 $target 的补签卡")
                                } else sender.sendMessage("${prefix}${ChatColor.YELLOW}用法: /$label correction_slip clear <player>")
                            }
                            else -> sender.sendMessage("${prefix}${ChatColor.YELLOW}子命令: give|decrease|clear")
                        }
                    } else sender.sendMessage("${prefix}${ChatColor.YELLOW}用法: /$label correction_slip <give|decrease|clear> ...")
                } else {
                    sender.sendMessage("${prefix}${ChatColor.RED}当前存储不支持补签卡操作")
                }
            }
            "make_up" -> {
                if (!sender.hasPermission("signinplus.make_up")) {
                    sender.sendMessage("${prefix}${ChatColor.RED}你没有权限执行此命令！")
                    return true
                }
                if (plugin.storage !is cc.vastsea.storage.SqliteStorage) {
                    sender.sendMessage("${prefix}${ChatColor.RED}当前存储不支持补签操作")
                    return true
                }

                val s = plugin.storage as cc.vastsea.storage.SqliteStorage
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
                    sender.sendMessage("${prefix}${ChatColor.RED}你没有权限为他人补签！")
                    return true
                }

                val finalForce = force || isAdmin
                val (madeUpDates, refundedCards) = s.makeUpSign(target, cards, finalForce)

                val today = java.time.LocalDate.now()
                val signedInToday = madeUpDates.contains(today)

                if (signedInToday) {
                    // Temporarily remove today's sign-in to correctly calculate streak and trigger rewards
                    s.getSignedDates(target).toMutableList().remove(today)
                }

                if (madeUpDates.isNotEmpty()) {
                    val pastDaysMadeUp = madeUpDates.filter { it.isBefore(today) }

                    if (pastDaysMadeUp.isNotEmpty()) {
                        var msg = "${prefix}${ChatColor.GREEN}已为您补签 ${pastDaysMadeUp.size} 次"
                        if (signedInToday) {
                            msg += "，且为今日签到了。"
                        } else {
                            msg += "。"
                        }
                        sender.sendMessage(msg)

                        if (refundedCards > 0) {
                            sender.sendMessage("${prefix}${ChatColor.YELLOW}退回 $refundedCards 张补签卡。")
                        }
                    } else if (signedInToday) {
                        sender.sendMessage("${prefix}${ChatColor.GREEN}没有可补签的，已为您今日签到。")
                    }

                    plugin.rewardExecutor.onSignedIn(target)

                    if (signedInToday) {
                        // Add today's sign-in back after rewards have been processed
                        s.getSignedDates(target).toMutableList().add(today)
                    }

                    val totalDays = s.getSignedDates(target).size
                    sender.sendMessage("${prefix}${ChatColor.GREEN}当前累计签到天数: $totalDays")
                } else {
                    sender.sendMessage("${prefix}${ChatColor.YELLOW}没有可补签的天数或补签卡不足。")
                }
            }
            "top" -> {
                if (plugin.storage is cc.vastsea.storage.SqliteStorage) {
                    val s = (plugin.storage as cc.vastsea.storage.SqliteStorage)
                    val mode = if (args.size >= 2) args[1].lowercase() else "total"
                    when (mode) {
                        "streak" -> showTop(sender, "连续签到天数前十：", s.topStreak(10))
                        else -> showTop(sender, "总天数排行前十：", s.topTotal(10))
                    }
                } else {
                    sender.sendMessage("${prefix}${ChatColor.RED}当前存储不支持排行操作")
                }
            }
            "debug" -> {
                if (!sender.hasPermission("signinplus.admin")) {
                    sender.sendMessage("${prefix}${ChatColor.RED}You do not have permission to use this command.")
                    return true
                }
                if (!plugin.config.getBoolean("debug", false)) {
                    sender.sendMessage("${prefix}${ChatColor.RED}Debug mode is not enabled in config.yml.")
                    return true
                }
                if (args.size < 2) {
                    sender.sendMessage("${prefix}${ChatColor.YELLOW}Usage: /$label debug trigger <type> [value]")
                    return true
                }
                when (args[1].lowercase()) {
                    "trigger" -> handleDebugTrigger(sender, args.drop(2))
                    else -> sender.sendMessage("${prefix}${ChatColor.YELLOW}Unknown debug command. Usage: /$label debug trigger <type> [value]")
                }
            }
            else -> {
                sender.sendMessage("${prefix}${ChatColor.YELLOW}未知子命令，使用 /$label help 查看帮助")
            }
        }
        return true
    }

    private fun showTop(sender: CommandSender, title: String, ranked: List<Pair<String, Int>>) {
        sender.sendMessage("${prefix}$title")
        for (i in 0 until 10) {
            val line = ranked.getOrNull(i)?.let { "${i+1}. ${it.first} - ${it.second}" } ?: "${i+1}. -"
            sender.sendMessage(line)
        }
    }

    private fun sendHelp(sender: CommandSender) {
        sender.sendMessage("${prefix}${ChatColor.AQUA}SignInPlus 指令帮助")
        sender.sendMessage("${ChatColor.GRAY}/signin ${ChatColor.WHITE}- 直接签到（同 /checkin、/qiandao、/qd）")
        sender.sendMessage("${ChatColor.GRAY}/signinplus help ${ChatColor.WHITE}- 查看帮助")
        sender.sendMessage("${ChatColor.GRAY}/signinplus status ${ChatColor.WHITE}- 查看今日签到状态")
        sender.sendMessage("${ChatColor.GRAY}/signinplus points [player] ${ChatColor.WHITE}- 查询自己或指定玩家积分（显示保留两位小数）")
        sender.sendMessage("${ChatColor.GRAY}/signinplus points set <player> <amount> ${ChatColor.WHITE}- 设置积分（管理员）")
        sender.sendMessage("${ChatColor.GRAY}/signinplus points decrease <player> <amount> ${ChatColor.WHITE}- 减少积分（管理员）")
        sender.sendMessage("${ChatColor.GRAY}/signinplus points clear <player> ${ChatColor.WHITE}- 清零积分（管理员）")
        sender.sendMessage("${ChatColor.GRAY}/signinplus correction_slip <give|decrease|clear> <player> [amount] ${ChatColor.WHITE}- 管理补签卡")
        sender.sendMessage("${ChatColor.GRAY}/signinplus make_up [cards] [player] [force] ${ChatColor.WHITE}- 为自己或指定玩家补签")
        sender.sendMessage("${ChatColor.GRAY}/signinplus top [total|streak] ${ChatColor.WHITE}- 查看排行前十（默认 total）")
        sender.sendMessage("${ChatColor.GRAY}/signinplus force_check_in <player> ${ChatColor.WHITE}- 强制为某人签到今日")
        sender.sendMessage("${ChatColor.GRAY}/signinplus reload ${ChatColor.WHITE}- 重载配置并重启 Web API")
    }

    private fun handleDebugTrigger(sender: CommandSender, args: List<String>) {
        if (args.isEmpty()) {
            sender.sendMessage("${prefix}${ChatColor.YELLOW}Usage: /signinplus debug trigger <type> [value]")
            return
        }

        val type = args[0].lowercase()
        val value = args.getOrNull(1)
        val player = if (sender is org.bukkit.entity.Player) sender else null

        if (player == null) {
            sender.sendMessage("${prefix}${ChatColor.RED}This command can only be run by a player.")
            return
        }

        sender.sendMessage("${prefix}${ChatColor.GREEN}Triggering debug reward for type '$type'...")

        when (type) {
            "default" -> plugin.rewardExecutor.runDefaultRewards(player.name)
            "cumulative" -> {
                val days = value?.toIntOrNull()
                if (days == null) {
                    sender.sendMessage("${prefix}${ChatColor.YELLOW}Usage: /signinplus debug trigger cumulative <days>")
                    return
                }
                val validDays = plugin.config.getMapList("cumulative").mapNotNull { it["times"]?.toString() }
                if (days.toString() !in validDays) {
                    sender.sendMessage("${prefix}${ChatColor.RED}Invalid days value. Available options: ${validDays.joinToString(" | ")}")
                    return
                }
                plugin.rewardExecutor.runCumulativeRewards(days, player.name, true)
            }
            "streak" -> {
                val days = value?.toIntOrNull()
                if (days == null) {
                    sender.sendMessage("${prefix}${ChatColor.YELLOW}Usage: /signinplus debug trigger streak <days>")
                    return
                }
                val validDays = plugin.config.getMapList("streak").mapNotNull { it["times"]?.toString() }
                if (days.toString() !in validDays) {
                    sender.sendMessage("${prefix}${ChatColor.RED}Invalid days value. Available options: ${validDays.joinToString(" | ")}")
                    return
                }
                plugin.rewardExecutor.runStreakRewards(days, player.name, true)
            }
            "top" -> {
                val rank = value?.toIntOrNull()
                if (rank == null) {
                    val ranks = plugin.config.getMapList("top").mapNotNull { it["rank"]?.toString() }.joinToString(" | ")
                    sender.sendMessage("${prefix}${ChatColor.YELLOW}Usage: /signinplus debug trigger top <rank>")
                    sender.sendMessage("${prefix}${ChatColor.GRAY}Available ranks: $ranks")
                    return
                }
                val validRanks = plugin.config.getMapList("top").mapNotNull { it["rank"]?.toString() }
                if (rank.toString() !in validRanks) {
                    sender.sendMessage("${prefix}${ChatColor.RED}Invalid rank value. Available options: ${validRanks.joinToString(" | ")}")
                    return
                }
                plugin.rewardExecutor.runTopRewards(rank, player.name, true)
            }
            "special_dates" -> {
                if (value == null) {
                    val dates = plugin.config.getMapList("special_dates").mapNotNull { it["date"]?.toString() }.joinToString(" | ")
                    sender.sendMessage("${prefix}${ChatColor.YELLOW}Usage: /signinplus debug trigger special_dates <date>")
                    sender.sendMessage("${prefix}${ChatColor.GRAY}Available dates: $dates")
                    return
                }
                val validDates = plugin.config.getMapList("special_dates").mapNotNull { it["date"]?.toString() }
                if (value !in validDates) {
                    sender.sendMessage("${prefix}${ChatColor.RED}Invalid date value. Available options: ${validDates.joinToString(" | ")}")
                    return
                }
                plugin.rewardExecutor.runSpecialDateRewards(value, player.name)
            }
            else -> {
                sender.sendMessage("${prefix}${ChatColor.YELLOW}Unknown trigger type: $type")
                return
            }
        }

        sender.sendMessage("${prefix}${ChatColor.GREEN}Debug trigger for '$type' executed for ${player.name}.")
    }

    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<out String>): MutableList<String> {
        val out = mutableListOf<String>()
        if (args.size == 1) {
            val base = listOf("help", "reload", "force_check_in", "status", "make_up", "correction_slip", "points", "top", "debug")
            out.addAll(base.filter { it.startsWith(args[0], ignoreCase = true) })
            return out
        }

        when (args[0].lowercase()) {
            "top" -> {
                if (args.size == 2) out.addAll(listOf("total", "streak").filter { it.startsWith(args[1], ignoreCase = true) })
            }
            "debug" -> {
                if (args.size == 2) {
                    out.addAll(listOf("trigger").filter { it.startsWith(args[1], ignoreCase = true) })
                }
                if (args.size == 3) {
                    out.addAll(listOf("default", "cumulative", "streak", "top", "special_dates").filter { it.startsWith(args[2], ignoreCase = true) })
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
            }
            "points" -> {
                if (args.size == 2) {
                    val subs = listOf("set", "decrease", "clear")
                    out.addAll(subs.filter { it.startsWith(args[1], ignoreCase = true) })
                    out.addAll(onlinePlayerNames().filter { it.startsWith(args[1], ignoreCase = true) })
                } else if (args.size == 3) {
                    when (args[1].lowercase()) {
                        "set", "decrease" -> out.addAll(onlinePlayerNames().filter { it.startsWith(args[2], ignoreCase = true) })
                        "clear" -> out.addAll(onlinePlayerNames().filter { it.startsWith(args[2], ignoreCase = true) })
                    }
                } else if (args.size == 4) {
                    when (args[1].lowercase()) {
                        "set", "decrease" -> out.addAll(suggestNumbers().filter { it.startsWith(args[3], ignoreCase = true) })
                    }
                }
            }
            "correction_slip" -> {
                when (args.size) {
                    2 -> out.addAll(listOf("give", "decrease", "clear").filter { it.startsWith(args[1], ignoreCase = true) })
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
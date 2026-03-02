package cc.vastsea.signinplus.gui

import cc.vastsea.signinplus.SignInPlus
import cc.vastsea.signinplus.storage.Checkins
import cc.vastsea.signinplus.storage.CorrectionSlips
import dev.triumphteam.gui.builder.item.ItemBuilder
import dev.triumphteam.gui.guis.Gui
import net.kyori.adventure.text.Component
import org.bukkit.Material
import org.bukkit.entity.Player
import java.time.LocalDate
import java.time.YearMonth

object SignInGui {

    private fun loc(key: String, placeholders: Map<String, String>? = null): String {
        return SignInPlus.localization.get(key, placeholders)
    }

    fun open(player: Player, yearMonth: YearMonth = YearMonth.now(SignInPlus.zoneId)) {
        val today = LocalDate.now(SignInPlus.zoneId)
        
        // Title: Sign In - 2023-10
        val title = loc("gui.title", mapOf("year" to yearMonth.year.toString(), "month" to yearMonth.monthValue.toString()))
        
        val gui = Gui.gui()
            .title(Component.text(title))
            .rows(6)
            .disableAllInteractions()
            .create()

        // Background filler
        gui.filler.fillBorder(ItemBuilder.from(Material.GRAY_STAINED_GLASS_PANE).name(Component.empty()).asGuiItem())

        // Remove slot 44 (last item of the second to last row) as requested
        gui.removeItem(44)
        
        // Stats Item (Row 0, Center: Slot 4)
        val streak = Checkins.getStreakDays(player.uniqueId)
        val total = Checkins.getTotalDays(player.uniqueId)
        val statsName = loc("gui.stats.name")
        val streakStr = loc("gui.stats.streak", mapOf("days" to streak.toString()))
        val totalStr = loc("gui.stats.total", mapOf("days" to total.toString()))
        
        val statsItem = ItemBuilder.from(Material.PAPER)
            .name(Component.text(statsName))
            .lore(
                Component.text(streakStr),
                Component.text(totalStr)
            )
            .asGuiItem()
        gui.setItem(4, statsItem)

        // Calendar Logic
        // The YearMonth class automatically handles:
        // - 31 days for Jan, Mar, May, Jul, Aug, Oct, Dec (腊月)
        // - 30 days for Apr, Jun, Sep, Nov
        // - 28 or 29 days for Feb (Leap year logic)
        val daysInMonth = yearMonth.lengthOfMonth()
        val signedDates = Checkins.getSignedDates(player.uniqueId).toSet()

        val canMakeUp = player.hasPermission("signinplus.make_up")
        val slips = if (canMakeUp) CorrectionSlips.getCorrectionSlipAmount(player.uniqueId) else 0

        // Slots 9 to 9 + daysInMonth - 1
        // Requirement: "从1号开始放在第二行第一个" -> Slot 9 (Row 1, Col 0)
        for (day in 1..daysInMonth) {
            val date = yearMonth.atDay(day)
            val isSigned = signedDates.contains(date)
            val isToday = date == today
            val isFuture = date.isAfter(today)

            val material = when {
                isSigned -> Material.LIME_STAINED_GLASS_PANE
                isToday -> Material.ORANGE_STAINED_GLASS_PANE 
                isFuture -> Material.WHITE_STAINED_GLASS_PANE
                else -> Material.RED_STAINED_GLASS_PANE // Missed
            }

            val dayName = if (isToday) loc("gui.status.today", mapOf("day" to day.toString())) 
                          else loc("gui.status.day", mapOf("day" to day.toString()))
            
            var status = when {
                isSigned -> loc("gui.status.signed")
                isToday -> loc("gui.buttons.sign_in")
                isFuture -> loc("gui.status.future")
                else -> loc("gui.status.missed")
            }

            // Make up logic
            if (!isSigned && !isFuture && !isToday && canMakeUp) {
                status = loc("gui.buttons.make_up")
            }

            val item = ItemBuilder.from(material)
                .name(Component.text(dayName))
                .lore(Component.text(status))

            if (!isSigned && !isFuture && !isToday && canMakeUp) {
                item.lore(
                    Component.text(status),
                    Component.text(loc("gui.status.slips", mapOf("amount" to slips.toString())))
                )
            }

            val guiItem = item.asGuiItem { _ ->
                    if (isToday && !isSigned) {
                        player.performCommand("signinplus")
                        // Refresh GUI after action
                        SignInPlus.instance.server.scheduler.runTask(SignInPlus.instance, Runnable {
                            open(player, yearMonth)
                        })
                    } else if (!isSigned && !isFuture && !isToday && canMakeUp) {
                         // Perform make up
                        player.performCommand("signinplus make_up 1")
                        SignInPlus.instance.server.scheduler.runTask(SignInPlus.instance, Runnable {
                            open(player, yearMonth)
                        })
                    }
                }
            
            val slotIndex = 9 + (day - 1)
            // Ensure we don't overflow into the bottom row (reserved for nav)
            // 9 + 30 = 39, max day is 31 -> 40. Safe within Row 1-4 (9-44).
            if (slotIndex < 45) { 
                gui.setItem(slotIndex, guiItem)
            }
        }

        // Navigation Buttons (Row 5: 45-53)
        
        // Previous Month (Slot 45 - Bottom Left)
        val prevItem = ItemBuilder.from(Material.ARROW)
            .name(Component.text(loc("gui.buttons.previous")))
            .asGuiItem {
                open(player, yearMonth.minusMonths(1))
            }
        gui.setItem(45, prevItem)

        // Next Month (Slot 53 - Bottom Right)
        val nextItem = ItemBuilder.from(Material.ARROW)
            .name(Component.text(loc("gui.buttons.next")))
            .asGuiItem {
                open(player, yearMonth.plusMonths(1))
            }
        gui.setItem(53, nextItem)

        // Close Button (Slot 49 - Bottom Center)
        val closeItem = ItemBuilder.from(Material.BARRIER)
            .name(Component.text(loc("gui.buttons.close")))
            .asGuiItem {
                gui.close(player)
            }
        gui.setItem(49, closeItem)

        // Sign In Button Shortcut (Slot 51, if today is in this view and not signed)
        // Removed as per request
        /*
        if (!Checkins.isSignedIn(player.uniqueId) && yearMonth == YearMonth.from(today)) {
             val signInItem = ItemBuilder.from(Material.NETHER_STAR)
                .name(Component.text(loc("gui.buttons.sign_in")))
                .asGuiItem {
                    player.performCommand("signinplus")
                    SignInPlus.instance.server.scheduler.runTask(SignInPlus.instance, Runnable {
                        open(player, yearMonth)
                    })
                }
            gui.setItem(51, signInItem)
        }
        */

        gui.open(player)
    }
}

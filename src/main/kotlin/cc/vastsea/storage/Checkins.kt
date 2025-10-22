package cc.vastsea.storage

import cc.vastsea.SignInPlus.Companion.now
import cc.vastsea.SignInPlus.Companion.today
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.javatime.date
import org.jetbrains.exposed.sql.javatime.time
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*
import kotlin.use


/*
CREATE TABLE IF NOT EXISTS checkins (
    player TEXT NOT NULL,
    day TEXT NOT NULL,
    time INTEGER NOT NULL
);
*/
object Checkins : Table() {
    val player = uuid("player").index()
    val day = date("day").index()
    val time = time("time")

    init {
        index(false, player, day)
        index(false, day, time)
    }

    fun isSignedIn(player: UUID): Boolean {
        val day = today()
        return transaction {
            Checkins.selectAll().where { Checkins.player.eq(player).and { Checkins.day.eq(day) } }.count() > 0
        }
    }

    fun signInToday(player: UUID) {
        val day = today()
        transaction {
            if (isSignedIn(player)) {
                return@transaction
            }
            Checkins.insert {
                it[Checkins.player] = player
                it[Checkins.day] = day
                it[Checkins.time] = now()
            }
        }
    }

    fun makeUpSign(player: UUID, cards: Int, force: Boolean): Pair<List<LocalDate>, Int> {
        val today = today()
        val isSignedInToday = isSignedIn(player)
        val missedDaysCount = getMissedDays(player)

        if (!isSignedInToday && missedDaysCount == 0) {
            signInToday(player)
            return Pair(listOf(today), cards)
        }

        val firstLaunchDate = PluginMeta.getFirstLaunchDate() ?: return Pair(emptyList(), cards)

        val signedDays = getSignedDates(player).map { it.toString() }.toSet()

        val missedDays = mutableListOf<LocalDate>()
        var currentDate = today.minusDays(1)
        while (!currentDate.isBefore(firstLaunchDate)) {
            if (!signedDays.contains(currentDate.toString())) {
                missedDays.add(currentDate)
            }
            currentDate = currentDate.minusDays(1)
        }

        if (missedDays.isEmpty()) {
            if (!isSignedInToday) {
                signInToday(player)
                return Pair(listOf(today), cards)
            }
            return Pair(emptyList(), cards) // No days to make up, return all cards
        }

        val slipsToUse =
            if (force) cards.coerceAtMost(missedDays.size) else CorrectionSlips.getCorrectionSlipAmount(player)
                .coerceAtMost(cards)
                .coerceAtMost(missedDays.size)
        if (!force) {
            CorrectionSlips.decreaseCorrectionSlip(player, slipsToUse)
        }
        val daysToSign = missedDays.take(slipsToUse)

        transaction {
            Checkins.batchInsert(daysToSign) { dayToSign ->
                this[Checkins.player] = player
                this[Checkins.day] = dayToSign
                this[Checkins.time] = now()
            }
        }

        val refundedCards = cards - slipsToUse

        val madeUpDays = daysToSign.toMutableList()
        if (!isSignedInToday) {
            signInToday(player)
            madeUpDays.add(today)
        }

        return Pair(madeUpDays, refundedCards)
    }

    fun getTotalDays(player: UUID): Int {
        return transaction {
            Checkins.selectAll().where { Checkins.player eq player }
                .withDistinct()
                .count()
                .toInt()
        }
    }

    fun getStreakDays(player: UUID): Int {
        // 计算以“玩家最近一次签到日”为基准的连续天数（兼容补签不含今日）
        val days = transaction {
            Checkins.selectAll().where { Checkins.player eq player }
                .withDistinct()
                .map { it[Checkins.day].toString() }
                .toSet()
        }
        if (days.isEmpty()) return 0
        var latestDay = transaction {
            Checkins.selectAll().where { Checkins.player eq player }
                .orderBy(Checkins.day, SortOrder.DESC)
                .limit(1)
                .firstOrNull()
                ?.get(Checkins.day)
        } ?: return 0
        var streak = 0
        while (days.contains(latestDay.toString())) {
            streak += 1
            latestDay = latestDay.minusDays(1)
        }
        return streak
    }


    fun getLastCheckInTime(player: UUID): String {
        return transaction {
            val time = Checkins.selectAll().where { Checkins.player eq player }
                .orderBy(Checkins.time, SortOrder.DESC)
                .limit(1)
                .firstOrNull()
                ?.get(Checkins.time)
            time?.toString() ?: "未签到"
        }
    }

    fun getRankToday(player: UUID): String {
        return transaction {
            val day = today()
            val checkinsToday = Checkins.selectAll().where { Checkins.day eq day }
                .orderBy(Checkins.time, SortOrder.ASC)
                .map { it[Checkins.player] }

            val rank = checkinsToday.indexOfFirst { it == player }
            if (rank != -1) {
                (rank + 1).toString()
            } else {
                "未签到"
            }
        }
    }

    fun getAmountToday(): Int {
        return transaction {
            val day = today()
            Checkins.selectAll().where { Checkins.day eq day }
                .withDistinct()
                .count()
                .toInt()
        }
    }

    fun topTotal(limit: Int): List<Pair<UUID, Int>> {
        return transaction {
            Checkins.select(player, player.countDistinct())
                .groupBy(Checkins.player)
                .orderBy(Checkins.player.countDistinct(), SortOrder.DESC)
                .limit(limit)
                .map { it[Checkins.player] to it[Checkins.player.countDistinct()].toInt() }
        }
    }

    fun topStreak(limit: Int): List<Pair<UUID, Int>> {
        // 简化：计算每玩家 streak，再排序（效率一般，但足够）
        val players = transaction {
            Checkins.select(player).withDistinct().map { it[Checkins.player] }
        }
        val ranked = players.map { it to getStreakDays(it) }.sortedByDescending { it.second }.take(limit)
        return ranked
    }

    fun getSignedDates(player: UUID): List<LocalDate> {
        return transaction {
            Checkins.selectAll().where { Checkins.player eq player }
                .orderBy(Checkins.day, SortOrder.ASC)
                .map { it[Checkins.day] }
        }
    }

    fun getMissedDays(player: UUID): Int {
        val firstDay = PluginMeta.getFirstLaunchDate() ?: return 0
        val signedDates = getSignedDates(player).toSet()
        if (firstDay !in signedDates) return 0 // Not even signed once

        var missed = 0
        var currentDate = firstDay
        val today = today()

        while (currentDate.isBefore(today)) {
            if (currentDate !in signedDates) {
                missed++
            }
            currentDate = currentDate.plusDays(1)
        }
        return missed
    }
}
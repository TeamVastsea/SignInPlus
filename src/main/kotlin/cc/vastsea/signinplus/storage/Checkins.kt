package cc.vastsea.signinplus.storage

import cc.vastsea.signinplus.SignInPlus
import cc.vastsea.signinplus.SignInPlus.Companion.now
import cc.vastsea.signinplus.SignInPlus.Companion.today
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.javatime.date
import org.jetbrains.exposed.sql.javatime.time
import org.jetbrains.exposed.sql.transactions.transaction
import java.nio.ByteBuffer
import java.sql.Connection
import java.sql.ResultSet
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*
import kotlin.use


/*
CREATE TABLE IF NOT EXISTS checkins (
    player_uuid UUID NOT NULL,
    day TEXT NOT NULL,
    time INTEGER NOT NULL
);
*/
object Checkins : Table() {
    val player = uuid("player_uuid").index()
    val day = date("day").index()
    val time = time("time")

    override val primaryKey = PrimaryKey(player, day, name = "pk_checkins")

    init {
        index(false, day, time)
    }

    fun isSignedIn(player: UUID): Boolean {
        val day = today()
        return transaction(DatabaseHelper.database) {
            Checkins.selectAll().where { Checkins.player.eq(player).and { Checkins.day.eq(day) } }.count() > 0
        }
    }

    fun signInToday(player: UUID): Boolean {
        val day = today()
        return transaction(DatabaseHelper.database) {
            Checkins.insertIgnore {
                it[Checkins.player] = player
                it[Checkins.day] = day
                it[Checkins.time] = now()
            }.insertedCount > 0
        }
    }

    fun makeUpSign(player: UUID, cards: Int, force: Boolean): Pair<List<LocalDate>, Int> {
        val currentDay = today()
        val requestedCards = cards.coerceAtLeast(0)
        return transaction(DatabaseHelper.database) {
            val signedDays = Checkins.selectAll().where { Checkins.player eq player }
                .map { it[Checkins.day] }
                .toMutableSet()
            val firstLaunchDate = PluginMeta.selectAll()
                .where { PluginMeta.key eq "first_launch_day" }
                .firstOrNull()?.get(PluginMeta.value)?.let(LocalDate::parse)
                ?: currentDay
            val firstEligibleDate = signedDays.minOrNull()?.let { maxOf(it, firstLaunchDate) } ?: currentDay

            val missedDays = mutableListOf<LocalDate>()
            var currentDate = currentDay.minusDays(1)
            while (!currentDate.isBefore(firstEligibleDate)) {
                if (currentDate !in signedDays) missedDays += currentDate
                currentDate = currentDate.minusDays(1)
            }

            val availableSlips = if (force) Int.MAX_VALUE else CorrectionSlips.getAmountForUpdate(player)
            val insertionLimit = minOf(requestedCards, missedDays.size, availableSlips)
            val daysToSign = mutableListOf<LocalDate>()
            for (dayToSign in missedDays) {
                if (daysToSign.size >= insertionLimit) break
                val inserted = Checkins.insertIgnore {
                    it[Checkins.player] = player
                    it[Checkins.day] = dayToSign
                    it[Checkins.time] = now()
                }.insertedCount > 0
                if (inserted) daysToSign += dayToSign
            }
            if (!force && daysToSign.isNotEmpty()) {
                CorrectionSlips.setAmountInTransaction(player, availableSlips - daysToSign.size)
            }

            val madeUpDays = daysToSign.toMutableList()
            if (currentDay !in signedDays) {
                val inserted = Checkins.insertIgnore {
                    it[Checkins.player] = player
                    it[Checkins.day] = currentDay
                    it[Checkins.time] = now()
                }.insertedCount > 0
                if (inserted) madeUpDays += currentDay
            }

            Pair(madeUpDays, requestedCards - daysToSign.size)
        }
    }

    fun forceSignDate(player: UUID, date: LocalDate): Boolean {
        return transaction(DatabaseHelper.database) {
            Checkins.insertIgnore {
                it[Checkins.player] = player
                it[Checkins.day] = date
                it[Checkins.time] = java.time.LocalTime.MIN
            }.insertedCount > 0
        }
    }

    fun makeUpDate(player: UUID, date: LocalDate): Boolean {
        val currentDay = today()
        if (!date.isBefore(currentDay)) return false
        return transaction(DatabaseHelper.database) {
            val signedDays = Checkins.selectAll().where { Checkins.player eq player }
                .map { it[Checkins.day] }
                .toSet()
            if (date in signedDays) return@transaction false
            val firstLaunchDate = PluginMeta.selectAll()
                .where { PluginMeta.key eq "first_launch_day" }
                .firstOrNull()?.get(PluginMeta.value)?.let(LocalDate::parse)
                ?: return@transaction false
            val firstEligibleDate = signedDays.minOrNull()?.let { maxOf(it, firstLaunchDate) }
                ?: return@transaction false
            if (date.isBefore(firstEligibleDate)) return@transaction false

            val available = CorrectionSlips.getAmountForUpdate(player)
            if (available <= 0) return@transaction false
            val inserted = Checkins.insertIgnore {
                it[Checkins.player] = player
                it[Checkins.day] = date
                it[Checkins.time] = java.time.LocalTime.MIN
            }.insertedCount > 0
            if (!inserted) return@transaction false
            CorrectionSlips.setAmountInTransaction(player, available - 1)
            true
        }
    }

    fun getTotalDays(player: UUID): Int {
        return transaction(DatabaseHelper.database) {
            Checkins.select(Checkins.day.countDistinct()).where { Checkins.player eq player }
                .first()[Checkins.day.countDistinct()].toInt()
        }
    }

    fun getStreakDays(player: UUID): Int {
        // 计算以“玩家最近一次签到日”为基准的连续天数（兼容补签不含今日）
        val days = transaction(DatabaseHelper.database) {
            Checkins.selectAll().where { Checkins.player eq player }
                .orderBy(Checkins.day, SortOrder.DESC)
                .map { it[Checkins.day] }
                .toSet()
        }
        if (days.isEmpty()) return 0
        var latestDay = days.maxOrNull() ?: return 0
        var streak = 0
        while (days.contains(latestDay)) {
            streak += 1
            latestDay = latestDay.minusDays(1)
        }
        return streak
    }


    fun getLastCheckInTime(player: UUID): String {
        return transaction(DatabaseHelper.database) {
            val row = Checkins.selectAll().where { Checkins.player eq player }
                .orderBy(Checkins.day, SortOrder.DESC)
                .orderBy(Checkins.time, SortOrder.DESC)
                .limit(1)
                .firstOrNull()
            if (row == null) return@transaction SignInPlus.localization.get("commands.status.not_signed_in")
            LocalDateTime.of(row[Checkins.day], row[Checkins.time])
                .atZone(SignInPlus.zoneId)
                .toOffsetDateTime()
                .toString()
        }
    }

    fun getRankToday(player: UUID): String {
        return transaction(DatabaseHelper.database) {
            val day = today()
            val checkinsToday = Checkins.selectAll().where { Checkins.day eq day }
                .orderBy(Checkins.time, SortOrder.ASC)
                .map { it[Checkins.player] }

            val rank = checkinsToday.indexOfFirst { it == player }
            if (rank != -1) {
                (rank + 1).toString()
            } else {
                SignInPlus.localization.get("commands.status.not_signed_in")
            }
        }
    }

    fun getAmountToday(): Int {
        return transaction(DatabaseHelper.database) {
            val day = today()
            Checkins.selectAll().where { Checkins.day eq day }
                .withDistinct()
                .count()
                .toInt()
        }
    }

    fun topTotal(limit: Int): List<Pair<UUID, Int>> {
        return transaction(DatabaseHelper.database) {
            val dayCount = Checkins.day.countDistinct()
            Checkins.select(player, dayCount)
                .groupBy(Checkins.player)
                .orderBy(dayCount, SortOrder.DESC)
                .limit(limit)
                .map { it[Checkins.player] to it[dayCount].toInt() }
        }
    }

    fun topStreak(limit: Int): List<Pair<UUID, Int>> {
        val safeLimit = limit.coerceIn(1, 1_000)
        return transaction(DatabaseHelper.database) {
            val jdbc = connection.connection as Connection
            val product = jdbc.metaData.databaseProductName.lowercase(Locale.ROOT)
            val q = jdbc.metaData.identifierQuoteString.trim().ifBlank { "\"" }
            fun ident(value: String) = q + value.replace(q, q + q) + q
            val table = ident(Checkins.tableName.lowercase(Locale.ROOT))
            val playerColumn = ident("player_uuid")
            val dayColumn = ident("day")
            val groupExpression = when {
                "postgresql" in product ->
                    "$dayColumn - CAST(ROW_NUMBER() OVER (PARTITION BY $playerColumn ORDER BY $dayColumn) AS INTEGER)"
                "mysql" in product ->
                    "TO_DAYS($dayColumn) - ROW_NUMBER() OVER (PARTITION BY $playerColumn ORDER BY $dayColumn)"
                "sqlite" in product ->
                    "CAST(julianday($dayColumn) AS INTEGER) - ROW_NUMBER() OVER " +
                        "(PARTITION BY $playerColumn ORDER BY $dayColumn)"
                else -> error("Unsupported database for streak ranking: ${jdbc.metaData.databaseProductName}")
            }
            val sql = """
                WITH ordered_days AS (
                    SELECT $playerColumn AS player_uuid, $dayColumn AS sign_day,
                           $groupExpression AS streak_group
                    FROM $table
                ), streak_runs AS (
                    SELECT player_uuid, COUNT(*) AS streak, MAX(sign_day) AS end_day
                    FROM ordered_days
                    GROUP BY player_uuid, streak_group
                ), latest_days AS (
                    SELECT $playerColumn AS player_uuid, MAX($dayColumn) AS latest_day
                    FROM $table
                    GROUP BY $playerColumn
                )
                SELECT runs.player_uuid, runs.streak
                FROM streak_runs runs
                JOIN latest_days latest
                  ON latest.player_uuid = runs.player_uuid
                 AND latest.latest_day = runs.end_day
                ORDER BY runs.streak DESC, runs.player_uuid
                LIMIT ?
            """.trimIndent()

            jdbc.prepareStatement(sql).use { statement ->
                statement.setInt(1, safeLimit)
                statement.executeQuery().use { rows ->
                    buildList {
                        while (rows.next()) {
                            add(rows.uuid("player_uuid") to rows.getInt("streak"))
                        }
                    }
                }
            }
        }
    }

    private fun ResultSet.uuid(column: String): UUID = when (val raw = getObject(column)) {
        is UUID -> raw
        is ByteArray -> ByteBuffer.wrap(raw).let { UUID(it.long, it.long) }
        else -> UUID.fromString(raw.toString())
    }

    fun getSignedDates(player: UUID): List<LocalDate> {
        return transaction(DatabaseHelper.database) {
            Checkins.selectAll().where { Checkins.player eq player }
                .orderBy(Checkins.day, SortOrder.ASC)
                .map { it[Checkins.day] }
                .distinct()
        }
    }

    fun getMissedDays(player: UUID): Int {
        val signedDates = getSignedDates(player).toSet()
        val firstSignedDay = signedDates.minOrNull() ?: return 0
        val firstDay = maxOf(PluginMeta.getFirstLaunchDate() ?: firstSignedDay, firstSignedDay)

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

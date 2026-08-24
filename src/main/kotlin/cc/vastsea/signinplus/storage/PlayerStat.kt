package cc.vastsea.signinplus.storage

import cc.vastsea.signinplus.SignInPlus
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDateTime
import java.util.UUID

data class PlayerStat(
    val uuid: UUID,
    val signedToday: Boolean,
    val totalDays: Int,
    val streakDays: Int,
    val missedDays: Int,
    val lastCheckInTime: String,
    val rankToday: String,
    val points: Long,
    val correctionSlipAmount: Int
) {
    companion object {
        fun load(playerId: UUID): PlayerStat = transaction(DatabaseHelper.database) {
            val dates = Checkins.selectAll().where { Checkins.player eq playerId }
                .map { it[Checkins.day] }
                .toSet()
            val today = SignInPlus.today()
            val firstSigned = dates.minOrNull()
            val firstLaunch = PluginMeta.selectAll().where { PluginMeta.key eq "first_launch_day" }
                .firstOrNull()?.get(PluginMeta.value)?.let(java.time.LocalDate::parse)
            val firstEligible = if (firstSigned == null) null else maxOf(firstSigned, firstLaunch ?: firstSigned)
            val missed = if (firstEligible == null) 0 else generateSequence(firstEligible) { it.plusDays(1) }
                .takeWhile { it.isBefore(today) }
                .count { it !in dates }

            var cursor = dates.maxOrNull()
            var streak = 0
            while (cursor != null && cursor in dates) {
                streak++
                cursor = cursor.minusDays(1)
            }

            val lastRow = Checkins.selectAll().where { Checkins.player eq playerId }
                .orderBy(Checkins.day, SortOrder.DESC)
                .orderBy(Checkins.time, SortOrder.DESC)
                .limit(1)
                .firstOrNull()
            val lastTime = lastRow?.let {
                LocalDateTime.of(it[Checkins.day], it[Checkins.time])
                    .atZone(SignInPlus.zoneId).toOffsetDateTime().toString()
            } ?: SignInPlus.localization.get("commands.status.not_signed_in")

            val todayPlayers = Checkins.selectAll().where { Checkins.day eq today }
                .orderBy(Checkins.time, SortOrder.ASC)
                .map { it[Checkins.player] }
            val rankIndex = todayPlayers.indexOf(playerId)
            val rank = if (rankIndex >= 0) (rankIndex + 1).toString()
                else SignInPlus.localization.get("commands.status.not_signed_in")

            val pointValue = Points.selectAll().where { Points.player eq playerId }
                .firstOrNull()?.get(Points.points) ?: 0L
            val slips = CorrectionSlips.selectAll().where { CorrectionSlips.player eq playerId }
                .firstOrNull()?.get(CorrectionSlips.amount) ?: 0

            PlayerStat(
                uuid = playerId,
                signedToday = today in dates,
                totalDays = dates.size,
                streakDays = streak,
                missedDays = missed,
                lastCheckInTime = lastTime,
                rankToday = rank,
                points = pointValue,
                correctionSlipAmount = slips
            )
        }
    }
}

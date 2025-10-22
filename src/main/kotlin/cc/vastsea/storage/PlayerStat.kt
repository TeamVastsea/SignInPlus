package cc.vastsea.storage

import java.util.UUID

data class PlayerStat(
    val uuid: UUID,
    var totalDays: Int = 0,
    var streakDays: Int = 0,
    var lastCheckInTime: String = "未签到",
    var rankToday: String = "未签到",
    var points: Double = 0.0,
    var correctionSlipAmount: Int = 0
) {
    fun PlayerStat(player: UUID): PlayerStat {
        return PlayerStat(
            uuid = player,
            totalDays = Checkins.getTotalDays(player),
            streakDays = Checkins.getStreakDays(player),
            lastCheckInTime = Checkins.getLastCheckInTime(player),
            rankToday = Checkins.getRankToday(player),
            points = Points.getPoints(player),
            correctionSlipAmount = CorrectionSlips.getCorrectionSlipAmount(player)
        )
    }
}
package cc.vastsea.storage

import cc.vastsea.SignInPlus
import java.util.UUID

data class PlayerStat(
    val uuid: UUID,
    var totalDays: Int = 0,
    var streakDays: Int = 0,
    var lastCheckInTime: String = SignInPlus.localization.get("commands.status.not_signed_in"),
    var rankToday: String = SignInPlus.localization.get("commands.status.not_signed_in"),
    var points: Double = 0.0,
    var correctionSlipAmount: Int = 0
) {
    constructor(player: UUID) : this(
        uuid = player,
        totalDays = Checkins.getTotalDays(player),
        streakDays = Checkins.getStreakDays(player),
        lastCheckInTime = Checkins.getLastCheckInTime(player),
        rankToday = Checkins.getRankToday(player),
        points = Points.getPoints(player),
        correctionSlipAmount = CorrectionSlips.getCorrectionSlipAmount(player)
    )
}
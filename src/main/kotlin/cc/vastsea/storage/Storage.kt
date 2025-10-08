package cc.vastsea.storage

import java.time.LocalDate

data class PlayerStat(
    val name: String,
    var totalDays: Int = 0,
    var streakDays: Int = 0,
    var lastCheckInTime: String = "未签到",
    var rankToday: String = "未签到",
    var points: Double = 0.0,
    var correctionSlipAmount: Int = 0
)

interface Storage {
    fun isSignedIn(player: String): Boolean
    fun getTotalDays(player: String): Int
    fun getStreakDays(player: String): Int
    fun getSignedDates(player: String): List<LocalDate>
    fun getLastCheckInTime(player: String): String
    fun getRankToday(player: String): String
    fun getPoints(player: String): Double
    fun getInfo(player: String): PlayerStat
    fun getAmountToday(): Int
    fun getMissedDays(player: String): Int

    fun makeUpSign(player: String, cards: Int, force: Boolean): Pair<List<LocalDate>, Int>

    // 奖励领取状态（防重复发放）
    fun hasClaimedTotalReward(player: String, times: Int): Boolean
    fun markClaimedTotalReward(player: String, times: Int)
    fun hasClaimedStreakReward(player: String, times: Int): Boolean
    fun markClaimedStreakReward(player: String, times: Int)
}
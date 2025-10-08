package cc.vastsea.storage

import java.time.LocalDate

class InMemoryStorage : Storage {
    private val players = mutableMapOf<String, PlayerStat>()
    private val claimedTotal = mutableMapOf<String, MutableSet<Int>>()
    private val claimedStreak = mutableMapOf<String, MutableSet<Int>>()
    private val firstLaunchDate: LocalDate = LocalDate.now()
    private val checkIns = mutableMapOf<String, MutableSet<LocalDate>>()

    private fun stat(player: String): PlayerStat = players.getOrPut(player.lowercase()) { PlayerStat(name = player) }

    override fun isSignedIn(player: String): Boolean {
        val signedDays = checkIns.getOrPut(player.lowercase()) { mutableSetOf() }
        return signedDays.contains(LocalDate.now())
    }

    override fun getTotalDays(player: String): Int {
        return checkIns.getOrPut(player.lowercase()) { mutableSetOf() }.size
    }

    override fun getStreakDays(player: String): Int {
        val signedDays = checkIns.getOrPut(player.lowercase()) { mutableSetOf() }
        if (signedDays.isEmpty()) return 0

        val latestDay = signedDays.maxOrNull() ?: return 0

        var streak = 0
        var currentDate = latestDay
        while (signedDays.contains(currentDate)) {
            streak++
            currentDate = currentDate.minusDays(1)
        }
        return streak
    }

    override fun getLastCheckInTime(player: String): String = stat(player).lastCheckInTime

    override fun getRankToday(player: String): String = stat(player).rankToday

    override fun getPoints(player: String): Double = stat(player).points

    override fun getInfo(player: String): PlayerStat {
        return players.getOrPut(player) { PlayerStat(player) }
    }

    override fun getMissedDays(player: String): Int {
        val stat = getInfo(player)
        val signedDates = getSignedDates(player).toSet()
        if (signedDates.isEmpty()) return 0

        val firstDate = signedDates.minOrNull() ?: return 0
        val today = LocalDate.now()
        var missed = 0
        var currentDate = firstDate
        while (currentDate.isBefore(today)) {
            if (currentDate !in signedDates) {
                missed++
            }
            currentDate = currentDate.plusDays(1)
        }
        return missed
    }

    override fun getAmountToday(): Int {
        val today = LocalDate.now().toString()
        return checkIns.values.count { it.contains(LocalDate.now()) }
    }

    override fun makeUpSign(player: String, cards: Int, force: Boolean): Pair<List<LocalDate>, Int> {
        val today = LocalDate.now()
        val isSignedInToday = isSignedIn(player)
        val missedDaysCount = getMissedDays(player)

        if (!isSignedInToday && missedDaysCount == 0) {
            val signedDays = checkIns.getOrPut(player.lowercase()) { mutableSetOf() }
            signedDays.add(today)
            return Pair(listOf(today), cards)
        }

        val signedDays = checkIns.getOrPut(player.lowercase()) { mutableSetOf() }

        val missedDays = mutableListOf<LocalDate>()
        var currentDate = today.minusDays(1)
        while (!currentDate.isBefore(firstLaunchDate)) {
            if (!signedDays.contains(currentDate)) {
                missedDays.add(currentDate)
            }
            currentDate = currentDate.minusDays(1)
        }

        if (missedDays.isEmpty()) {
            if (!isSignedInToday) {
                signedDays.add(today)
                return Pair(listOf(today), cards)
            }
            return Pair(emptyList(), cards)
        }

        val slipsToUse = if (force) cards.coerceAtMost(missedDays.size) else stat(player).correctionSlipAmount.coerceAtMost(cards).coerceAtMost(missedDays.size)
        if (!force) {
            stat(player).correctionSlipAmount -= slipsToUse
        }
        val daysToSign = missedDays.take(slipsToUse)
        daysToSign.forEach { signedDays.add(it) }

        val refundedCards = cards - slipsToUse

        val madeUpDays = daysToSign.toMutableList()
        if (!isSignedInToday) {
            signedDays.add(today)
            madeUpDays.add(today)
        }

        return Pair(madeUpDays, refundedCards)
    }

    override fun hasClaimedTotalReward(player: String, times: Int): Boolean {
        return claimedTotal.getOrPut(player.lowercase()) { mutableSetOf() }.contains(times)
    }

    override fun markClaimedTotalReward(player: String, times: Int) {
        claimedTotal.getOrPut(player.lowercase()) { mutableSetOf() }.add(times)
    }

    override fun hasClaimedStreakReward(player: String, times: Int): Boolean {
        return claimedStreak.getOrPut(player.lowercase()) { mutableSetOf() }.contains(times)
    }

    override fun markClaimedStreakReward(player: String, times: Int) {
        claimedStreak.getOrPut(player.lowercase()) { mutableSetOf() }.add(times)
    }

    override fun getSignedDates(player: String): List<LocalDate> {
        return checkIns.getOrPut(player.lowercase()) { mutableSetOf() }.toList()
    }
}
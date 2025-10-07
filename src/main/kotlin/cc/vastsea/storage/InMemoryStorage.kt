package cc.vastsea.storage

class InMemoryStorage : Storage {
    private val players = mutableMapOf<String, PlayerStat>()
    private var amountToday: Int = 0
    private val claimedTotal = mutableMapOf<String, MutableSet<Int>>()
    private val claimedStreak = mutableMapOf<String, MutableSet<Int>>()

    private fun stat(player: String): PlayerStat = players.getOrPut(player.lowercase()) { PlayerStat(name = player) }

    override fun isSignedIn(player: String): Boolean = stat(player).signedInToday

    override fun getTotalDays(player: String): Int = stat(player).totalDays

    override fun getStreakDays(player: String): Int = stat(player).streakDays

    override fun getLastCheckInTime(player: String): String = stat(player).lastCheckInTime

    override fun getRankToday(player: String): String = stat(player).rankToday

    override fun getPoints(player: String): Double = stat(player).points

    override fun getInfo(player: String): PlayerStat = stat(player)

    override fun getAmountToday(): Int = amountToday

    // 便于后续扩展：模拟签到写入
    fun mockSignIn(player: String, rank: Int? = null) {
        val s = stat(player)
        if (!s.signedInToday) {
            s.signedInToday = true
            amountToday += 1
        }
        s.totalDays += 1
        s.streakDays += 1
        s.lastCheckInTime = System.currentTimeMillis().toString()
        s.rankToday = rank?.toString() ?: s.rankToday
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
}
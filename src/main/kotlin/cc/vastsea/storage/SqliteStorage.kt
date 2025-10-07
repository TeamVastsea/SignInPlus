package cc.vastsea.storage

import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class SqliteStorage(dbPath: String, timezone: String) : Storage {
    private val zoneId: ZoneId = ZoneId.of(timezone)
    private val conn: Connection = DriverManager.getConnection("jdbc:sqlite:$dbPath")

    init {
        conn.createStatement().use { st ->
            st.executeUpdate(
                """
                CREATE TABLE IF NOT EXISTS checkins (
                  player TEXT NOT NULL,
                  day TEXT NOT NULL,
                  time INTEGER NOT NULL
                );
                """.trimIndent()
            )
            st.executeUpdate(
                """
                CREATE TABLE IF NOT EXISTS points (
                  player TEXT PRIMARY KEY,
                  points REAL NOT NULL DEFAULT 0
                );
                """.trimIndent()
            )
            st.executeUpdate(
                """
                CREATE TABLE IF NOT EXISTS correction_slips (
                  player TEXT PRIMARY KEY,
                  amount INTEGER NOT NULL DEFAULT 0
                );
                """.trimIndent()
            )
            st.executeUpdate(
                """
                CREATE TABLE IF NOT EXISTS claimed_rewards (
                  player TEXT NOT NULL,
                  type TEXT NOT NULL,
                  times INTEGER NOT NULL,
                  claimed_time INTEGER NOT NULL,
                  UNIQUE(player, type, times)
                );
                """.trimIndent()
            )
            st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_checkins_player_day ON checkins(player, day);")
            st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_checkins_day_time ON checkins(day, time);")
        }
    }

    private fun today(): String = LocalDate.now(zoneId).toString()

    fun signInToday(player: String) {
        val day = today()
        conn.prepareStatement("SELECT 1 FROM checkins WHERE player=? AND day=? LIMIT 1").use { ps ->
            ps.setString(1, player)
            ps.setString(2, day)
            ps.executeQuery().use { rs ->
                if (rs.next()) return
            }
        }
        conn.prepareStatement("INSERT INTO checkins(player, day, time) VALUES(?,?,?)").use { ps ->
            ps.setString(1, player)
            ps.setString(2, day)
            ps.setLong(3, System.currentTimeMillis())
            ps.executeUpdate()
        }
    }

    fun makeUpDays(player: String, days: Int) {
        if (days <= 0) return
        val start = LocalDate.now(zoneId).minusDays(1)
        var d = start
        var remaining = days
        while (remaining > 0) {
            val day = d.toString()
            conn.prepareStatement("SELECT 1 FROM checkins WHERE player=? AND day=? LIMIT 1").use { ps ->
                ps.setString(1, player)
                ps.setString(2, day)
                ps.executeQuery().use { rs ->
                    if (!rs.next()) {
                        conn.prepareStatement("INSERT INTO checkins(player, day, time) VALUES(?,?,?)").use { ips ->
                            ips.setString(1, player)
                            ips.setString(2, day)
                            ips.setLong(3, System.currentTimeMillis())
                            ips.executeUpdate()
                        }
                        remaining -= 1
                    }
                }
            }
            d = d.minusDays(1)
        }
    }

    override fun isSignedIn(player: String): Boolean {
        conn.prepareStatement("SELECT 1 FROM checkins WHERE player=? AND day=? LIMIT 1").use { ps ->
            ps.setString(1, player)
            ps.setString(2, today())
            ps.executeQuery().use { rs -> return rs.next() }
        }
    }

    override fun getTotalDays(player: String): Int {
        conn.prepareStatement("SELECT COUNT(DISTINCT day) FROM checkins WHERE player=?").use { ps ->
            ps.setString(1, player)
            ps.executeQuery().use { rs -> return if (rs.next()) rs.getInt(1) else 0 }
        }
    }

    override fun getStreakDays(player: String): Int {
        // 计算以“玩家最近一次签到日”为基准的连续天数（兼容补签不含今日）
        val days = mutableSetOf<String>()
        conn.prepareStatement("SELECT DISTINCT day FROM checkins WHERE player=?").use { ps ->
            ps.setString(1, player)
            ps.executeQuery().use { rs ->
                while (rs.next()) days.add(rs.getString(1))
            }
        }
        if (days.isEmpty()) return 0
        val latestDayStr = run {
            conn.prepareStatement("SELECT day FROM checkins WHERE player=? ORDER BY day DESC LIMIT 1").use { ps ->
                ps.setString(1, player)
                ps.executeQuery().use { rs -> if (rs.next()) rs.getString(1) else null }
            }
        } ?: return 0
        var streak = 0
        var d = LocalDate.parse(latestDayStr)
        while (days.contains(d.toString())) {
            streak += 1
            d = d.minusDays(1)
        }
        return streak
    }

    override fun getLastCheckInTime(player: String): String {
        conn.prepareStatement("SELECT time FROM checkins WHERE player=? ORDER BY time DESC LIMIT 1").use { ps ->
            ps.setString(1, player)
            ps.executeQuery().use { rs ->
                return if (rs.next()) {
                    val t = LocalDateTime.ofEpochSecond(rs.getLong(1) / 1000, 0, java.time.ZoneOffset.UTC)
                    t.atZone(zoneId).toLocalDateTime().toString()
                } else "未签到"
            }
        }
    }

    override fun getRankToday(player: String): String {
        val day = today()
        conn.prepareStatement("SELECT player, time FROM checkins WHERE day=? ORDER BY time ASC").use { ps ->
            ps.setString(1, day)
            ps.executeQuery().use { rs ->
                var rank = 1
                while (rs.next()) {
                    if (rs.getString(1).equals(player, ignoreCase = true)) return rank.toString()
                    rank += 1
                }
            }
        }
        return "未签到"
    }

    override fun getPoints(player: String): Double {
        conn.prepareStatement("SELECT points FROM points WHERE player=?").use { ps ->
            ps.setString(1, player)
            ps.executeQuery().use { rs -> return if (rs.next()) rs.getDouble(1) else 0.0 }
        }
    }

    fun setPoints(player: String, points: Double) {
        conn.prepareStatement("INSERT INTO points(player, points) VALUES(?,?) ON CONFLICT(player) DO UPDATE SET points=excluded.points").use { ps ->
            ps.setString(1, player)
            ps.setDouble(2, points)
            ps.executeUpdate()
        }
    }

    fun addPoints(player: String, delta: Double) {
        val now = getPoints(player)
        setPoints(player, now + delta)
    }

    fun getCorrectionSlipAmount(player: String): Int {
        conn.prepareStatement("SELECT amount FROM correction_slips WHERE player=?").use { ps ->
            ps.setString(1, player)
            ps.executeQuery().use { rs -> return if (rs.next()) rs.getInt(1) else 0 }
        }
    }

    fun giveCorrectionSlip(player: String, amount: Int) {
        val now = getCorrectionSlipAmount(player)
        val next = (now + amount).coerceAtLeast(0)
        conn.prepareStatement("INSERT INTO correction_slips(player, amount) VALUES(?,?) ON CONFLICT(player) DO UPDATE SET amount=excluded.amount").use { ps ->
            ps.setString(1, player)
            ps.setInt(2, next)
            ps.executeUpdate()
        }
    }

    fun decreaseCorrectionSlip(player: String, amount: Int) {
        giveCorrectionSlip(player, -amount)
    }

    fun clearCorrectionSlip(player: String) {
        conn.prepareStatement("DELETE FROM correction_slips WHERE player=?").use { ps ->
            ps.setString(1, player)
            ps.executeUpdate()
        }
    }

    override fun getInfo(player: String): PlayerStat {
        return PlayerStat(
            name = player,
            totalDays = getTotalDays(player),
            streakDays = getStreakDays(player),
            lastCheckInTime = getLastCheckInTime(player),
            rankToday = getRankToday(player),
            points = getPoints(player),
            signedInToday = isSignedIn(player),
            correctionSlipAmount = getCorrectionSlipAmount(player),
        )
    }

    override fun getAmountToday(): Int {
        conn.prepareStatement("SELECT COUNT(*) FROM (SELECT DISTINCT player FROM checkins WHERE day=? ) t").use { ps ->
            ps.setString(1, today())
            ps.executeQuery().use { rs -> return if (rs.next()) rs.getInt(1) else 0 }
        }
    }

    override fun hasClaimedTotalReward(player: String, times: Int): Boolean {
        conn.prepareStatement("SELECT 1 FROM claimed_rewards WHERE player=? AND type='total' AND times=? LIMIT 1").use { ps ->
            ps.setString(1, player)
            ps.setInt(2, times)
            ps.executeQuery().use { rs -> return rs.next() }
        }
    }

    override fun markClaimedTotalReward(player: String, times: Int) {
        conn.prepareStatement("INSERT OR IGNORE INTO claimed_rewards(player, type, times, claimed_time) VALUES(?,?,?,?)").use { ps ->
            ps.setString(1, player)
            ps.setString(2, "total")
            ps.setInt(3, times)
            ps.setLong(4, System.currentTimeMillis())
            ps.executeUpdate()
        }
    }

    override fun hasClaimedStreakReward(player: String, times: Int): Boolean {
        conn.prepareStatement("SELECT 1 FROM claimed_rewards WHERE player=? AND type='streak' AND times=? LIMIT 1").use { ps ->
            ps.setString(1, player)
            ps.setInt(2, times)
            ps.executeQuery().use { rs -> return rs.next() }
        }
    }

    override fun markClaimedStreakReward(player: String, times: Int) {
        conn.prepareStatement("INSERT OR IGNORE INTO claimed_rewards(player, type, times, claimed_time) VALUES(?,?,?,?)").use { ps ->
            ps.setString(1, player)
            ps.setString(2, "streak")
            ps.setInt(3, times)
            ps.setLong(4, System.currentTimeMillis())
            ps.executeUpdate()
        }
    }

    fun topTotal(limit: Int): List<Pair<String, Int>> {
        conn.createStatement().use { st ->
            st.executeQuery(
                "SELECT player, COUNT(DISTINCT day) AS total FROM checkins GROUP BY player ORDER BY total DESC, player ASC LIMIT $limit"
            ).use { rs ->
                val list = mutableListOf<Pair<String, Int>>()
                while (rs.next()) list.add(rs.getString(1) to rs.getInt(2))
                return list
            }
        }
    }

    fun topStreak(limit: Int): List<Pair<String, Int>> {
        // 简化：计算每玩家 streak，再排序（效率一般，但足够）
        val players = mutableSetOf<String>()
        conn.createStatement().use { st ->
            st.executeQuery("SELECT DISTINCT player FROM checkins").use { rs ->
                while (rs.next()) players.add(rs.getString(1))
            }
        }
        val ranked = players.map { it to getStreakDays(it) }.sortedByDescending { it.second }.take(limit)
        return ranked
    }
}
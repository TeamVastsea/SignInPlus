package cc.vastsea.storage

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SortOrder
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class OrmStorage(
    type: String,
    timezone: String,
    sqlitePath: String? = null,
    host: String? = null,
    port: Int? = null,
    database: String? = null,
    username: String? = null,
    password: String? = null,
    sslMode: String? = null,
) : Storage {
    private val zoneId: ZoneId = ZoneId.of(timezone)

    object Checkins : Table("checkins") {
        val player = varchar("player", 64)
        val day = varchar("day", 32)
        val time = long("time")
        init {
            uniqueIndex(player, day)
            index(false, day, time)
        }
    }

    object Points : Table("points") {
        val player = varchar("player", 64)
        val points = double("points").default(0.0)
        override val primaryKey = PrimaryKey(player)
    }

    object CorrectionSlips : Table("correction_slips") {
        val player = varchar("player", 64)
        val amount = integer("amount").default(0)
        override val primaryKey = PrimaryKey(player)
    }

    object ClaimedRewards : Table("claimed_rewards") {
        val player = varchar("player", 64)
        val type = varchar("type", 32)
        val times = integer("times")
        val claimedTime = long("claimed_time")
        init { uniqueIndex(player, type, times) }
    }

    object PluginMeta : Table("plugin_meta") {
        val key = varchar("key", 64)
        val value = varchar("value", 128)
        override val primaryKey = PrimaryKey(key)
    }

    init {
        when (type.lowercase()) {
            "sqlite" -> {
                val url = "jdbc:sqlite:$sqlitePath"
                println("[SignInPlus] JDBC URL: $url")
                Database.connect(url, driver = "org.sqlite.JDBC")
            }
            "mysql" -> {
                val finalPort = port ?: 3306
                val url = "jdbc:mysql://$host:$finalPort/$database?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC"
                println("[SignInPlus] JDBC URL: $url (user=$username)")
                Database.connect(url, driver = "com.mysql.cj.jdbc.Driver", user = username ?: "", password = password ?: "")
            }
            "postgresql", "pgsql", "postgres" -> {
                val finalPort = port ?: 5432
                val mode = sslMode ?: "disable"
                val url = "jdbc:postgresql://$host:$finalPort/$database?sslmode=$mode&connectTimeout=5"
                println("[SignInPlus] JDBC URL: $url (user=$username)")
                try {
                    Class.forName("org.postgresql.Driver")
                    var attempt = 0
                    val maxAttempts = 15
                    while (true) {
                        try {
                            java.sql.DriverManager.getConnection(url, username ?: "", password ?: "").use { conn ->
                                println("[SignInPlus] PostgreSQL preflight OK: ${conn.metaData.databaseProductName} ${conn.metaData.databaseProductVersion}")
                            }
                            break
                        } catch (e: Throwable) {
                            attempt += 1
                            if (attempt >= maxAttempts) {
                                println("[SignInPlus] PostgreSQL preflight failed after $maxAttempts attempts: ${e.message}")
                                break
                            }
                            println("[SignInPlus] PostgreSQL not ready (${e.message}), retrying in 2s... [$attempt/$maxAttempts]")
                            Thread.sleep(2000)
                        }
                    }
                } catch (cnfe: ClassNotFoundException) {
                    println("[SignInPlus] PostgreSQL driver not found on classpath: ${cnfe.message}")
                }
                Database.connect(url, driver = "org.postgresql.Driver", user = username ?: "", password = password ?: "")
            }
            else -> throw IllegalArgumentException("Unsupported storage type: $type")
        }
        run {
            var attempt = 0
            val maxAttempts = 5
            while (true) {
                try {
                    transaction {
                        SchemaUtils.createMissingTablesAndColumns(Checkins, Points, CorrectionSlips, ClaimedRewards, PluginMeta)
                        val exists = PluginMeta.select { PluginMeta.key eq "first_launch_date" }.count() > 0
                        if (!exists) {
                            PluginMeta.insert {
                                it[key] = "first_launch_date"
                                it[value] = LocalDate.now(zoneId).toString()
                            }
                        }
                    }
                    break
                } catch (t: Throwable) {
                    attempt += 1
                    if (attempt >= maxAttempts) {
                        println("[SignInPlus] Database init failed after $maxAttempts attempts: ${t.message}")
                        throw t
                    }
                    println("[SignInPlus] Database not ready (${t.message}), retrying in 2s... [$attempt/$maxAttempts]")
                    Thread.sleep(2000)
                }
            }
        }


    }

    private fun today(): String = LocalDate.now(zoneId).toString()

    override fun signInToday(player: String) {
        transaction {
            val dayStr = today()
            val exists = Checkins.select { (Checkins.player eq player) and (Checkins.day eq dayStr) }.count() > 0
            if (!exists) {
                Checkins.insert {
                    it[Checkins.player] = player
                    it[day] = dayStr
                    it[time] = System.currentTimeMillis()
                }
            }
        }
    }

    override fun makeUpSign(player: String, cards: Int, force: Boolean): Pair<List<LocalDate>, Int> {
        val todayDate = LocalDate.now(zoneId)
        val isSignedInToday = isSignedIn(player)
        val missedDaysCount = getMissedDays(player)

        if (!isSignedInToday && missedDaysCount == 0) {
            signInToday(player)
            return Pair(listOf(todayDate), cards)
        }

        val firstLaunchDateStr = transaction {
            PluginMeta.select { PluginMeta.key eq "first_launch_date" }.limit(1).firstOrNull()?.get(PluginMeta.value)
                ?: LocalDate.now(zoneId).toString()
        }
        val firstLaunchDate = LocalDate.parse(firstLaunchDateStr)

        val signedDays = transaction {
            Checkins.select { Checkins.player eq player }.map { it[Checkins.day] }.toMutableSet()
        }

        val missedDays = mutableListOf<LocalDate>()
        var currentDate = todayDate.minusDays(1)
        while (!currentDate.isBefore(firstLaunchDate)) {
            if (!signedDays.contains(currentDate.toString())) {
                missedDays.add(currentDate)
            }
            currentDate = currentDate.minusDays(1)
        }

        if (missedDays.isEmpty()) {
            if (!isSignedInToday) {
                signInToday(player)
                return Pair(listOf(todayDate), cards)
            }
            return Pair(emptyList(), cards)
        }

        val slipsOwned = getCorrectionSlipAmount(player)
        val slipsToUse = if (force) cards.coerceAtMost(missedDays.size) else slipsOwned.coerceAtMost(cards).coerceAtMost(missedDays.size)
        if (!force) decreaseCorrectionSlip(player, slipsToUse)
        val daysToSign = missedDays.take(slipsToUse)

        transaction {
            daysToSign.forEach { d ->
                val ds = d.toString()
                val exists = Checkins.select { (Checkins.player eq player) and (Checkins.day eq ds) }.count() > 0
                if (!exists) {
                    Checkins.insert {
                        it[Checkins.player] = player
                        it[day] = ds
                        it[time] = System.currentTimeMillis()
                    }
                }
            }
        }

        val refundedCards = cards - slipsToUse
        val madeUpDays = daysToSign.toMutableList()
        if (!isSignedInToday) {
            signInToday(player)
            madeUpDays.add(todayDate)
        }
        return Pair(madeUpDays, refundedCards)
    }

    override fun isSignedIn(player: String): Boolean = transaction {
        Checkins.select { (Checkins.player eq player) and (Checkins.day eq today()) }.count() > 0
    }

    override fun getTotalDays(player: String): Int = transaction {
        Checkins.select { Checkins.player eq player }.map { it[Checkins.day] }.distinct().count()
    }

    override fun getStreakDays(player: String): Int {
        val daysSet = transaction {
            Checkins.select { Checkins.player eq player }.map { it[Checkins.day] }.toMutableSet()
        }
        if (daysSet.isEmpty()) return 0
        val latestDayStr = transaction {
            Checkins.select { Checkins.player eq player }
                .orderBy(Checkins.day, SortOrder.DESC)
                .limit(1)
                .firstOrNull()?.get(Checkins.day)
        } ?: return 0
        var streak = 0
        var d = LocalDate.parse(latestDayStr)
        while (daysSet.contains(d.toString())) {
            streak += 1
            d = d.minusDays(1)
        }
        return streak
    }

    override fun getLastCheckInTime(player: String): String {
        val millis = transaction {
            Checkins.slice(Checkins.time).select { Checkins.player eq player }
                .orderBy(Checkins.time, SortOrder.DESC).limit(1)
                .firstOrNull()?.get(Checkins.time)
        } ?: return "未签到"
        val t = LocalDateTime.ofEpochSecond(millis / 1000, 0, java.time.ZoneOffset.UTC)
        return t.atZone(zoneId).toLocalDateTime().toString()
    }

    override fun getPoints(player: String): Double = transaction {
        Points.select { Points.player eq player }.limit(1).firstOrNull()?.get(Points.points) ?: 0.0
    }

    override fun setPoints(player: String, points: Double) {
        transaction {
            val exists = Points.select { Points.player eq player }.count() > 0
            if (exists) {
                Points.update({ Points.player eq player }) { it[Points.points] = points }
            } else {
                Points.insert {
                    it[Points.player] = player
                    it[Points.points] = points
                }
            }
        }
    }

    override fun addPoints(player: String, delta: Double) {
        transaction {
            val exists = Points.select { Points.player eq player }.count() > 0
            if (exists) {
                val current = Points.select { Points.player eq player }.limit(1).firstOrNull()?.get(Points.points) ?: 0.0
                Points.update({ Points.player eq player }) { it[Points.points] = (current + delta) }
            } else {
                Points.insert {
                    it[Points.player] = player
                    it[Points.points] = delta
                }
            }
        }
    }

    override fun giveCorrectionSlip(player: String, amount: Int) {
        transaction {
            val exists = CorrectionSlips.select { CorrectionSlips.player eq player }.count() > 0
            if (exists) {
                val current = CorrectionSlips.select { CorrectionSlips.player eq player }.limit(1).firstOrNull()?.get(CorrectionSlips.amount) ?: 0
                CorrectionSlips.update({ CorrectionSlips.player eq player }) { it[CorrectionSlips.amount] = (current + amount) }
            } else {
                CorrectionSlips.insert {
                    it[CorrectionSlips.player] = player
                    it[CorrectionSlips.amount] = amount
                }
            }
        }
    }

    override fun getCorrectionSlipAmount(player: String): Int = transaction {
        CorrectionSlips.select { CorrectionSlips.player eq player }.limit(1).firstOrNull()?.get(CorrectionSlips.amount) ?: 0
    }

    override fun decreaseCorrectionSlip(player: String, amount: Int) {
        transaction {
            val exists = CorrectionSlips.select { CorrectionSlips.player eq player }.count() > 0
            if (exists) {
                val current = CorrectionSlips.select { CorrectionSlips.player eq player }.limit(1).first()[CorrectionSlips.amount]
                val newAmount = (current - amount).coerceAtLeast(0)
                CorrectionSlips.update({ CorrectionSlips.player eq player }) { it[CorrectionSlips.amount] = newAmount }
            }
        }
    }

    override fun clearCorrectionSlip(player: String) {
        transaction {
            val exists = CorrectionSlips.select { CorrectionSlips.player eq player }.count() > 0
            if (exists) {
                CorrectionSlips.update({ CorrectionSlips.player eq player }) { it[CorrectionSlips.amount] = 0 }
            }
        }
    }

    override fun getInfo(player: String): PlayerStat {
        val total = getTotalDays(player)
        val streak = getStreakDays(player)
        val last = getLastCheckInTime(player)
        val rank = getRankToday(player)
        val pts = getPoints(player)
        val slip = getCorrectionSlipAmount(player)
        return PlayerStat(
            name = player,
            totalDays = total,
            streakDays = streak,
            lastCheckInTime = last,
            rankToday = rank,
            points = pts,
            correctionSlipAmount = slip
        )
    }

    override fun getAmountToday(): Int = transaction {
        Checkins.select { Checkins.day eq today() }.count().toInt()
    }

    override fun getMissedDays(player: String): Int {
        val firstLaunchDateStr = transaction {
            PluginMeta.select { PluginMeta.key eq "first_launch_date" }
                .limit(1)
                .firstOrNull()?.get(PluginMeta.value)
        } ?: return 0
        val firstDay = LocalDate.parse(firstLaunchDateStr)
        val signedDates = transaction {
            Checkins.select { Checkins.player eq player }
                .orderBy(Checkins.day, SortOrder.ASC)
                .map { LocalDate.parse(it[Checkins.day]) }
                .toSet()
        }
        if (firstDay !in signedDates) return 0
        var missed = 0
        var currentDate = firstDay
        val todayDate = LocalDate.now(zoneId)
        while (currentDate.isBefore(todayDate)) {
            if (currentDate !in signedDates) missed++
            currentDate = currentDate.plusDays(1)
        }
        return missed
    }

    override fun hasClaimedTotalReward(player: String, times: Int): Boolean = transaction {
        ClaimedRewards.select { (ClaimedRewards.player eq player) and (ClaimedRewards.type eq "total") and (ClaimedRewards.times eq times) }.count() > 0
    }

    override fun markClaimedTotalReward(player: String, times: Int) {
        transaction {
            ClaimedRewards.insert {
                it[ClaimedRewards.player] = player
                it[type] = "total"
                it[ClaimedRewards.times] = times
                it[claimedTime] = System.currentTimeMillis()
            }
        }
    }

    override fun hasClaimedStreakReward(player: String, times: Int): Boolean = transaction {
        ClaimedRewards.select { (ClaimedRewards.player eq player) and (ClaimedRewards.type eq "streak") and (ClaimedRewards.times eq times) }.count() > 0
    }

    override fun markClaimedStreakReward(player: String, times: Int) {
        transaction {
            ClaimedRewards.insert {
                it[ClaimedRewards.player] = player
                it[type] = "streak"
                it[ClaimedRewards.times] = times
                it[claimedTime] = System.currentTimeMillis()
            }
        }
    }

    override fun topTotal(limit: Int): List<Pair<String, Int>> = transaction {
        Checkins
            .slice(Checkins.player, Checkins.day.countDistinct())
            .selectAll()
            .groupBy(Checkins.player)
            .orderBy(Checkins.day.countDistinct() to SortOrder.DESC)
            .limit(limit)
            .map { it[Checkins.player] to it[Checkins.day.countDistinct()].toInt() }
    }

    override fun topStreak(limit: Int): List<Pair<String, Int>> {
        val all = transaction {
            Checkins.selectAll().map { it[Checkins.player] to it[Checkins.day] }.groupBy({ it.first }, { it.second })
        }
        return all.map { (player, days) ->
            val set = days.toSet()
            val latest = days.maxOrNull()?.let { LocalDate.parse(it) } ?: return@map player to 0
            var streak = 0
            var d = latest
            while (set.contains(d.toString())) { streak += 1; d = d.minusDays(1) }
            player to streak
        }.sortedByDescending { it.second }.take(limit)
    }

    override fun getRankToday(player: String): String {
        val dayStr = today()
        val players = transaction {
            Checkins.select { Checkins.day eq dayStr }
                .orderBy(Checkins.time, SortOrder.ASC)
                .map { it[Checkins.player] }
        }
        var rank = 1
        for (p in players) {
            if (p.equals(player, ignoreCase = true)) return rank.toString()
            rank += 1
        }
        return "未签到"
    }

    override fun getSignedDates(player: String): List<LocalDate> = transaction {
        Checkins.select { Checkins.player eq player }
            .orderBy(Checkins.day, SortOrder.ASC)
            .map { LocalDate.parse(it[Checkins.day]) }
    }
}
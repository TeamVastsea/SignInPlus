package cc.vastsea.signinplus.storage

import cc.vastsea.signinplus.SignInPlus
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import java.time.ZoneId
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import kotlin.test.assertEquals

@EnabledIfEnvironmentVariable(named = "SIGNINPLUS_TEST_POSTGRES_URL", matches = ".+")
class PostgresConcurrencyTest {
    private lateinit var database: Database

    @BeforeEach
    fun setUp() {
        database = Database.connect(
            url = requireNotNull(System.getenv("SIGNINPLUS_TEST_POSTGRES_URL")),
            driver = "org.postgresql.Driver",
        )
        DatabaseHelper.useDatabaseForTests(database)
        SignInPlus.setZoneIdForTests(ZoneId.of("Asia/Shanghai"))
        transaction(database) {
            SchemaUtils.drop(
                Checkins,
                ClaimedRewards,
                CorrectionSlips,
                PluginMeta,
                PlayerProfiles,
                Points,
                SpecialDateClaims,
            )
            SchemaUtils.create(
                Checkins,
                ClaimedRewards,
                CorrectionSlips,
                PluginMeta,
                PlayerProfiles,
                Points,
                SpecialDateClaims,
            )
        }
        PluginMeta.initFirstLaunchDay()
    }

    @Test
    fun `concurrent subservers preserve atomic player state`() {
        val player = UUID.randomUUID()

        assertEquals(1, concurrently(32) { Checkins.signInToday(player) }.count { it })
        assertEquals(1, Checkins.getTotalDays(player))

        concurrently(100) { Points.addPoints(player, 1L) }
        assertEquals(100L, Points.getPoints(player))

        concurrently(100) { CorrectionSlips.giveCorrectionSlip(player, 1) }
        assertEquals(100, CorrectionSlips.getCorrectionSlipAmount(player))

        assertEquals(1, concurrently(32) { ClaimedRewards.tryClaimTotalReward(player, 7) }.count { it })
        assertEquals(1, concurrently(32) { ClaimedRewards.tryClaimStreakReward(player, 3) }.count { it })
        assertEquals(5, concurrently(32) { SpecialDateClaims.tryClaim(player, "Monday", 5) }.count { it != null })
    }

    @Test
    fun `concurrent bulk make-up inserts each day and spends each slip once`() {
        val player = UUID.randomUUID()
        val today = SignInPlus.today()
        transaction(database) {
            PluginMeta.update({ PluginMeta.key eq "first_launch_day" }) {
                it[value] = today.minusDays(4).toString()
            }
        }
        Checkins.forceSignDate(player, today.minusDays(4))
        Checkins.signInToday(player)
        CorrectionSlips.giveCorrectionSlip(player, 3)

        concurrently(2) { Checkins.makeUpSign(player, 3, false) }

        assertEquals(5, Checkins.getTotalDays(player))
        assertEquals(0, CorrectionSlips.getCorrectionSlipAmount(player))
    }

    @Test
    fun `postgres calculates latest streak leaderboard in SQL`() {
        val first = UUID.fromString("10000000-0000-0000-0000-000000000001")
        val second = UUID.fromString("10000000-0000-0000-0000-000000000002")
        val today = SignInPlus.today()
        listOf(today.minusDays(2), today.minusDays(1), today).forEach { Checkins.forceSignDate(first, it) }
        listOf(today.minusDays(5), today.minusDays(4)).forEach { Checkins.forceSignDate(second, it) }

        assertEquals(listOf(first to 3, second to 2), Checkins.topStreak(2))
    }

    @Test
    fun `concurrent fresh subservers serialize schema creation`() {
        transaction(database) {
            SchemaUtils.drop(
                Checkins,
                ClaimedRewards,
                CorrectionSlips,
                PluginMeta,
                PlayerProfiles,
                Points,
                SpecialDateClaims,
            )
        }

        concurrently(2) {
            transaction(database) {
                DatabaseHelper.lockSchemaInitialization(connection.connection as java.sql.Connection)
                SchemaUtils.create(
                    Checkins,
                    ClaimedRewards,
                    CorrectionSlips,
                    PluginMeta,
                    PlayerProfiles,
                    Points,
                    SpecialDateClaims,
                )
            }
        }

        val player = UUID.randomUUID()
        assertEquals(1, concurrently(16) { Checkins.signInToday(player) }.count { it })
    }

    private fun <T> concurrently(tasks: Int, action: () -> T): List<T> {
        val executor = Executors.newFixedThreadPool(8)
        return try {
            executor.invokeAll(List(tasks) { Callable(action) }).map { it.get() }
        } finally {
            executor.shutdownNow()
        }
    }
}

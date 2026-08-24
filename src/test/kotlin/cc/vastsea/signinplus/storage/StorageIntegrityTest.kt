package cc.vastsea.signinplus.storage

import cc.vastsea.signinplus.SignInPlus
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.time.ZoneId
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StorageIntegrityTest {
    @TempDir
    lateinit var tempDir: Path

    private lateinit var database: Database

    @BeforeEach
    fun setUp() {
        database = newDatabase("storage.db")
        DatabaseHelper.useDatabaseForTests(database)
        SignInPlus.setZoneIdForTests(ZoneId.of("Asia/Shanghai"))
        transaction(database) {
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
    fun `sign-in is idempotent and counted once`() {
        val player = UUID.randomUUID()

        assertTrue(Checkins.signInToday(player))
        assertFalse(Checkins.signInToday(player))
        assertEquals(1, Checkins.getTotalDays(player))
    }

    @Test
    fun `multiple cumulative and streak thresholds can be claimed once`() {
        val player = UUID.randomUUID()

        assertTrue(ClaimedRewards.tryClaimTotalReward(player, 7))
        assertTrue(ClaimedRewards.tryClaimTotalReward(player, 30))
        assertFalse(ClaimedRewards.tryClaimTotalReward(player, 7))
        assertTrue(ClaimedRewards.tryClaimStreakReward(player, 3))
        assertTrue(ClaimedRewards.tryClaimStreakReward(player, 7))
        assertFalse(ClaimedRewards.tryClaimStreakReward(player, 3))
    }

    @Test
    fun `specific make-up consumes exactly one slip`() {
        val player = UUID.randomUUID()
        val today = SignInPlus.today()
        transaction(database) {
            PluginMeta.update({ PluginMeta.key eq "first_launch_day" }) {
                it[value] = today.minusDays(2).toString()
            }
        }
        Checkins.forceSignDate(player, today.minusDays(2))
        Checkins.signInToday(player)
        CorrectionSlips.giveCorrectionSlip(player, 1)

        assertTrue(Checkins.makeUpDate(player, today.minusDays(1)))
        assertEquals(0, CorrectionSlips.getCorrectionSlipAmount(player))
        assertFalse(Checkins.makeUpDate(player, today.minusDays(1)))
        assertEquals(0, CorrectionSlips.getCorrectionSlipAmount(player))
    }

    @Test
    fun `points and correction slips update existing values instead of losing increments`() {
        val player = UUID.randomUUID()

        Points.addPoints(player, 100L)
        Points.addPoints(player, 25L)
        CorrectionSlips.giveCorrectionSlip(player, 2)
        CorrectionSlips.giveCorrectionSlip(player, 3)

        assertEquals(125L, Points.getPoints(player))
        assertEquals(5, CorrectionSlips.getCorrectionSlipAmount(player))
    }

    @Test
    fun `profile name changes keep the same UUID identity`() {
        val playerUuid = UUID.randomUUID()

        PlayerProfiles.remember(playerUuid, "BeforeRename")
        PlayerProfiles.remember(playerUuid, "AfterRename")

        assertEquals(null, PlayerProfiles.resolveUuid("BeforeRename"))
        assertEquals(playerUuid, PlayerProfiles.resolveUuid("AfterRename"))
        assertTrue(PlayerProfiles.isNameDerivedOfflineUuid(PlayerProfiles.offlineUuid("OfflineName"), "OfflineName"))
        assertFalse(PlayerProfiles.isNameDerivedOfflineUuid(playerUuid, "OfflineName"))
    }

    @Test
    fun `streak leaderboard is calculated by the database`() {
        val first = UUID.fromString("00000000-0000-0000-0000-000000000001")
        val second = UUID.fromString("00000000-0000-0000-0000-000000000002")
        val third = UUID.fromString("00000000-0000-0000-0000-000000000003")
        val today = SignInPlus.today()

        listOf(today.minusDays(2), today.minusDays(1), today).forEach { Checkins.forceSignDate(first, it) }
        listOf(today.minusDays(4), today.minusDays(3)).forEach { Checkins.forceSignDate(second, it) }
        listOf(today.minusDays(2), today).forEach { Checkins.forceSignDate(third, it) }

        assertEquals(listOf(first to 3, second to 2, third to 1), Checkins.topStreak(10))
    }

    @Test
    fun `fresh schema uses UUID primary keys and a unique name lookup`() {
        val expectedPrimaryKeys = mapOf(
            Checkins.tableName to listOf("player_uuid", "day"),
            ClaimedRewards.tableName to listOf("player_uuid", "type", "times"),
            CorrectionSlips.tableName to listOf("player_uuid"),
            PlayerProfiles.tableName to listOf("player_uuid"),
            Points.tableName to listOf("player_uuid"),
            SpecialDateClaims.tableName to listOf("player_uuid", "rule"),
        )

        transaction(database) {
            val jdbc = connection.connection as java.sql.Connection
            expectedPrimaryKeys.forEach { (table, expected) ->
                val actual = buildList {
                    jdbc.metaData.getPrimaryKeys(jdbc.catalog, null, table).use { rows ->
                        while (rows.next()) {
                            add(rows.getInt("KEY_SEQ") to rows.getString("COLUMN_NAME").lowercase())
                        }
                    }
                }.sortedBy { it.first }.map { it.second }
                assertEquals(expected, actual, "primary key for $table")
            }

            val uniqueNameIndex = buildList {
                jdbc.metaData.getIndexInfo(jdbc.catalog, null, PlayerProfiles.tableName, true, false).use { rows ->
                    while (rows.next()) {
                        if (!rows.getBoolean("NON_UNIQUE")) {
                            rows.getString("COLUMN_NAME")?.lowercase()?.let(::add)
                        }
                    }
                }
            }
            assertTrue("normalized_name" in uniqueNameIndex)
        }
    }

    private fun newDatabase(fileName: String): Database = Database.connect(
        url = "jdbc:sqlite:${tempDir.resolve(fileName)}",
        driver = "org.sqlite.JDBC"
    )
}

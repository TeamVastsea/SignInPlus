package cc.vastsea.signinplus.storage

import cc.vastsea.signinplus.SignInPlus.Companion.now
import cc.vastsea.signinplus.SignInPlus.Companion.today
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.neq
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.javatime.datetime
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.nio.charset.StandardCharsets
import java.time.LocalDateTime
import java.util.Locale
import java.util.UUID

/**
 * A non-authoritative name lookup for UUID-keyed player data.
 *
 * UniversalAuth's profile UUID remains the identity. Names are only refreshed after the authenticated
 * profile reaches this backend and may be discarded/replaced without touching player data.
 */
object PlayerProfiles : Table() {
    val playerUuid = uuid("player_uuid")
    val currentName = varchar("current_name", 16)
    val normalizedName = varchar("normalized_name", 16).uniqueIndex()
    val updatedAt = datetime("updated_at")

    override val primaryKey = PrimaryKey(playerUuid, name = "pk_player_profiles")

    fun remember(uuid: UUID, name: String) {
        require(name.matches(Regex("[A-Za-z0-9_]{1,16}"))) { "Invalid Minecraft profile name" }
        val normalized = name.lowercase(Locale.ROOT)
        val timestamp = LocalDateTime.of(today(), now())
        transaction(DatabaseHelper.database) {
            PlayerProfiles.deleteWhere {
                (normalizedName eq normalized) and (playerUuid neq uuid)
            }
            PlayerProfiles.insertIgnore {
                it[playerUuid] = uuid
                it[currentName] = name
                it[normalizedName] = normalized
                it[updatedAt] = timestamp
            }
            PlayerProfiles.update({ playerUuid eq uuid }) {
                it[currentName] = name
                it[normalizedName] = normalized
                it[updatedAt] = timestamp
            }
        }
    }

    fun resolveUuid(name: String): UUID? {
        if (!name.matches(Regex("[A-Za-z0-9_]{1,16}"))) return null
        val normalized = name.lowercase(Locale.ROOT)
        return transaction(DatabaseHelper.database) {
            PlayerProfiles.selectAll().where { normalizedName eq normalized }
                .orderBy(updatedAt, SortOrder.DESC)
                .limit(1)
                .firstOrNull()
                ?.get(playerUuid)
        }
    }

    fun isNameDerivedOfflineUuid(uuid: UUID, name: String): Boolean = uuid == offlineUuid(name)

    internal fun offlineUuid(name: String): UUID = UUID.nameUUIDFromBytes(
        "OfflinePlayer:$name".toByteArray(StandardCharsets.UTF_8)
    )
}

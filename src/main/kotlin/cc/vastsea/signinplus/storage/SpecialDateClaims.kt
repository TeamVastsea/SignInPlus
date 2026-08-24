package cc.vastsea.signinplus.storage

import cc.vastsea.signinplus.SignInPlus.Companion.now
import cc.vastsea.signinplus.SignInPlus.Companion.today
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.javatime.datetime
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import org.jetbrains.exposed.sql.and
import java.time.LocalDateTime
import java.util.UUID

/*
CREATE TABLE IF NOT EXISTS special_date_claims (
    player_uuid UUID NOT NULL,
    rule TEXT NOT NULL,
    times INTEGER NOT NULL,
    last_claim_time INTEGER NOT NULL,
    UNIQUE(player, rule)
);
*/
object SpecialDateClaims : Table() {
    val player = uuid("player_uuid")
    val rule = varchar("rule", 64)
    val times = integer("times")
    val lastClaimTime = datetime("last_claim_time")

    override val primaryKey = PrimaryKey(player, rule, name = "pk_special_date_claims")

    fun tryClaim(playerId: UUID, ruleKey: String, limit: Int): Int? {
        if (limit <= 0) return null
        return transaction(DatabaseHelper.database) {
            SpecialDateClaims.insertIgnore {
                it[player] = playerId
                it[rule] = ruleKey
                it[times] = 0
                it[lastClaimTime] = LocalDateTime.of(today(), now())
            }
            val existing = SpecialDateClaims.selectAll().where {
                (SpecialDateClaims.player eq playerId) and (SpecialDateClaims.rule eq ruleKey)
            }.forUpdate().first()
            val current = existing[times]
            if (current >= limit) return@transaction null
            val next = current + 1
            SpecialDateClaims.update({
                (SpecialDateClaims.player eq playerId) and (SpecialDateClaims.rule eq ruleKey)
            }) {
                it[times] = next
                it[lastClaimTime] = LocalDateTime.of(today(), now())
            }
            next
        }
    }
}

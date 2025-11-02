package cc.vastsea.signinplus.storage

import cc.vastsea.signinplus.SignInPlus.Companion.now
import cc.vastsea.signinplus.SignInPlus.Companion.today
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.javatime.datetime
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import org.jetbrains.exposed.sql.and
import java.time.LocalDateTime
import java.util.UUID

/*
CREATE TABLE IF NOT EXISTS special_date_claims (
    player TEXT NOT NULL,
    rule TEXT NOT NULL,
    times INTEGER NOT NULL,
    last_claim_time INTEGER NOT NULL,
    UNIQUE(player, rule)
);
*/
object SpecialDateClaims : Table() {
    val player = uuid("player")
    val rule = varchar("rule", 64)
    val times = integer("times")
    val lastClaimTime = datetime("last_claim_time")

    init {
        uniqueIndex(player, rule)
    }

    fun getTimes(playerId: UUID, ruleKey: String): Int = transaction {
        SpecialDateClaims.selectAll().where {
            (SpecialDateClaims.player eq playerId) and (SpecialDateClaims.rule eq ruleKey)
        }.firstOrNull()?.get(times) ?: 0
    }

    fun increment(playerId: UUID, ruleKey: String) {
        transaction {
            val existing = SpecialDateClaims.selectAll().where {
                (SpecialDateClaims.player eq playerId) and (SpecialDateClaims.rule eq ruleKey)
            }.firstOrNull()
            if (existing == null) {
                SpecialDateClaims.insert {
                    it[player] = playerId
                    it[rule] = ruleKey
                    it[times] = 1
                    it[lastClaimTime] = LocalDateTime.of(today(), now())
                }
            } else {
                SpecialDateClaims.update({ (SpecialDateClaims.player eq playerId) and (SpecialDateClaims.rule eq ruleKey) }) {
                    it[times] = existing[times] + 1
                    it[lastClaimTime] = LocalDateTime.of(today(), now())
                }
            }
        }
    }
}
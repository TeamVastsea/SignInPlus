package cc.vastsea.storage

import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.util.UUID
import kotlin.use

/*
CREATE TABLE IF NOT EXISTS correction_slips (
    player TEXT PRIMARY KEY,
    amount INTEGER NOT NULL DEFAULT 0
);
*/
object CorrectionSlips : Table() {
    val player = uuid("player").uniqueIndex()
    val amount = integer("amount").default(0)

    fun getCorrectionSlipAmount(player: UUID): Int {
        return transaction {
            CorrectionSlips.selectAll().where {
                CorrectionSlips.player.eq(player)
            }.firstOrNull()?.get(CorrectionSlips.amount) ?: 0
        }
    }

    fun giveCorrectionSlip(player: UUID, amount: Int) {
        val now = getCorrectionSlipAmount(player)
        val next = (now + amount).coerceAtLeast(0)
        transaction {
            if (next == 0) {
                CorrectionSlips.deleteWhere { CorrectionSlips.player.eq(player) }
            } else {
                CorrectionSlips.insertIgnore {
                    it[CorrectionSlips.player] = player
                    it[CorrectionSlips.amount] = next
                }
                CorrectionSlips.update({ CorrectionSlips.player.eq(player) }) {
                    it[CorrectionSlips.amount] = next
                }
            }
        }
    }

    fun decreaseCorrectionSlip(player: UUID, amount: Int) {
        giveCorrectionSlip(player, -amount)
    }

    fun clearCorrectionSlip(player: UUID) {
        transaction {
            CorrectionSlips.deleteWhere { CorrectionSlips.player.eq(player) }
        }
    }
}
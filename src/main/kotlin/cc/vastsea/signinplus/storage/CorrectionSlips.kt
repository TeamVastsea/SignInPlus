package cc.vastsea.signinplus.storage

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
    player_uuid UUID PRIMARY KEY,
    amount INTEGER NOT NULL DEFAULT 0
);
*/
object CorrectionSlips : Table() {
    val player = uuid("player_uuid")
    val amount = integer("amount").default(0)

    override val primaryKey = PrimaryKey(player, name = "pk_correction_slips")

    fun getCorrectionSlipAmount(player: UUID): Int {
        return transaction(DatabaseHelper.database) {
            CorrectionSlips.selectAll().where {
                CorrectionSlips.player.eq(player)
            }.firstOrNull()?.get(CorrectionSlips.amount) ?: 0
        }
    }

    fun giveCorrectionSlip(player: UUID, amount: Int) {
        transaction(DatabaseHelper.database) {
            CorrectionSlips.insertIgnore {
                it[CorrectionSlips.player] = player
                it[CorrectionSlips.amount] = 0
            }
            val current = getAmountForUpdate(player)
            val next = (current.toLong() + amount.toLong()).coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
            setAmountInTransaction(player, next)
        }
    }

    fun decreaseCorrectionSlip(player: UUID, amount: Int) {
        giveCorrectionSlip(player, -amount)
    }

    fun clearCorrectionSlip(player: UUID) {
        transaction(DatabaseHelper.database) {
            CorrectionSlips.deleteWhere { CorrectionSlips.player.eq(player) }
        }
    }

    internal fun getAmountForUpdate(playerId: UUID): Int =
        CorrectionSlips.selectAll().where { player.eq(playerId) }
            .forUpdate()
            .firstOrNull()?.get(amount) ?: 0

    internal fun setAmountInTransaction(playerId: UUID, next: Int) {
        if (next <= 0) {
            CorrectionSlips.deleteWhere { player.eq(playerId) }
            return
        }
        CorrectionSlips.insertIgnore {
            it[player] = playerId
            it[amount] = next
        }
        CorrectionSlips.update({ player.eq(playerId) }) { it[amount] = next }
    }
}

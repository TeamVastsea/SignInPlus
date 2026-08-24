package cc.vastsea.signinplus.storage

import cc.vastsea.signinplus.SignInPlus.Companion.now
import cc.vastsea.signinplus.SignInPlus.Companion.today
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.javatime.datetime
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDateTime
import java.util.*

/*
CREATE TABLE IF NOT EXISTS claimed_rewards (
    player_uuid UUID NOT NULL,
    type TEXT NOT NULL,
    times INTEGER NOT NULL,
    claimed_time INTEGER NOT NULL,
    UNIQUE(player, type, times)
);
*/
object ClaimedRewards : Table() {
    val player = uuid("player_uuid")
    val type = enumeration("type", ClaimedType::class)
    val times = integer("times")
    val claimedTime = datetime("claimed_time")

    override val primaryKey = PrimaryKey(player, type, times, name = "pk_claimed_rewards")

    fun tryClaimTotalReward(player: UUID, times: Int): Boolean {
        return transaction(DatabaseHelper.database) {
            ClaimedRewards.insertIgnore {
                it[ClaimedRewards.player] = player
                it[type] = ClaimedType.TOTAL
                it[ClaimedRewards.times] = times
                it[claimedTime] = LocalDateTime.of(today(), now())
            }.insertedCount > 0
        }
    }

    fun tryClaimStreakReward(player: UUID, times: Int): Boolean {
        return transaction(DatabaseHelper.database) {
            ClaimedRewards.insertIgnore {
                it[ClaimedRewards.player] = player
                it[type] = ClaimedType.STREAK
                it[ClaimedRewards.times] = times
                it[claimedTime] = LocalDateTime.of(today(), now())
            }.insertedCount > 0
        }
    }

    enum class ClaimedType {
        TOTAL,
        STREAK
    }

}

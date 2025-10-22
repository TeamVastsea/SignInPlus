package cc.vastsea.storage

import cc.vastsea.SignInPlus.Companion.now
import cc.vastsea.SignInPlus.Companion.today
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.javatime.datetime
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDateTime
import java.util.*

/*
CREATE TABLE IF NOT EXISTS claimed_rewards (
    player TEXT NOT NULL,
    type TEXT NOT NULL,
    times INTEGER NOT NULL,
    claimed_time INTEGER NOT NULL,
    UNIQUE(player, type, times)
);
*/

object ClaimedRewards : Table() {
    val player = uuid("player")
    val type = enumeration("type", ClaimedType::class)
    val times = integer("times")
    val claimedTime = datetime("claimed_time")

    init {
        uniqueIndex(player, type)
    }

    fun hasClaimedTotalReward(player: UUID, times: Int): Boolean {
        return transaction {
            ClaimedRewards.selectAll().where {
                (ClaimedRewards.player eq player) and (type eq ClaimedType.TOTAL) and (ClaimedRewards.times eq times)
            }.count() > 0
        }
    }

    fun markClaimedTotalReward(player: UUID, times: Int) {
        transaction {
            ClaimedRewards.insert {
                it[ClaimedRewards.player] = player
                it[type] = ClaimedType.TOTAL
                it[ClaimedRewards.times] = times
                it[claimedTime] = LocalDateTime.of(today(), now())
            }
        }
    }

    fun hasClaimedStreakReward(player: UUID, times: Int): Boolean {
        return transaction {
            ClaimedRewards.selectAll().where {
                (ClaimedRewards.player eq player) and
                        (type eq ClaimedType.STREAK) and (ClaimedRewards.times eq times)
            }.count() > 0
        }
    }

    fun markClaimedStreakReward(player: UUID, times: Int) {
        transaction {
            ClaimedRewards.insert {
                it[ClaimedRewards.player] = player
                it[type] = ClaimedType.STREAK
                it[ClaimedRewards.times] = times
                it[claimedTime] = LocalDateTime.of(today(), now())
            }
        }
    }

    enum class ClaimedType {
        TOTAL,
        STREAK
    }

}
package cc.vastsea.storage

import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.util.UUID
import kotlin.use


/*
CREATE TABLE IF NOT EXISTS points (
    player TEXT PRIMARY KEY,
    points REAL NOT NULL DEFAULT 0
);
*/
object Points : Table() {
    val player = uuid("player").uniqueIndex()
    val points = double("points").default(0.0)

    fun getPoints(player: UUID): Double {
        return transaction {
            Points.selectAll().where { Points.player.eq(player) }
                .firstOrNull()?.get(Points.points) ?: 0.0
        }
    }

    fun setPoints(player: UUID, points: Double) {
        transaction {
            if (Points.selectAll().where { Points.player.eq(player) }.count() == 0L) {
                Points.insert {
                    it[Points.player] = player
                    it[Points.points] = points
                }
            } else {
                Points.update({ Points.player.eq(player) }) {
                    it[Points.points] = points
                }
            }
        }
    }

    fun addPoints(player: UUID, delta: Double) {
        val now = getPoints(player)
        setPoints(player, now + delta)
    }
}
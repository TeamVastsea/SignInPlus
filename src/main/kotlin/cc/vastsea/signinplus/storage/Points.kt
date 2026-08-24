package cc.vastsea.signinplus.storage

import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.util.UUID
import kotlin.use


/*
CREATE TABLE IF NOT EXISTS points (
    player_uuid UUID PRIMARY KEY,
    points_cents BIGINT NOT NULL DEFAULT 0
);
*/
object Points : Table() {
    val player = uuid("player_uuid")
    val points = long("points_cents").default(0L)

    override val primaryKey = PrimaryKey(player, name = "pk_points")

    fun getPoints(player: UUID): Long {
        return transaction(DatabaseHelper.database) {
            Points.selectAll().where { Points.player.eq(player) }
                .firstOrNull()?.get(Points.points) ?: 0L
        }
    }

    fun setPoints(player: UUID, points: Long) {
        transaction(DatabaseHelper.database) {
            Points.insertIgnore {
                it[Points.player] = player
                it[Points.points] = 0L
            }
            Points.update({ Points.player.eq(player) }) {
                it[Points.points] = points
            }
        }
    }

    fun addPoints(player: UUID, delta: Long) {
        transaction(DatabaseHelper.database) {
            Points.insertIgnore {
                it[Points.player] = player
                it[Points.points] = 0L
            }
            val current = Points.selectAll().where { Points.player.eq(player) }
                .forUpdate().first()[Points.points]
            val next = Math.addExact(current, delta)
            Points.update({ Points.player.eq(player) }) { it[Points.points] = next }
        }
    }
}

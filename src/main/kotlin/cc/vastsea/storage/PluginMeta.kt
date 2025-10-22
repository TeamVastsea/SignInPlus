package cc.vastsea.storage

import cc.vastsea.SignInPlus.Companion.today
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDate
import kotlin.use

/*
CREATE TABLE IF NOT EXISTS plugin_meta (
    key TEXT PRIMARY KEY,
    value TEXT NOT NULL
);
*/
object PluginMeta : Table() {
    val key = varchar("key", 100).uniqueIndex()
    val value = text("value")

    fun initFirstLaunchDay() {
        val day = today()
        transaction {
            if (PluginMeta.selectAll().count() == 0L) {
                PluginMeta.insert {
                    it[key] = "first_launch_day"
                    it[value] = day.toString()
                }
            }
        }
    }

    fun getFirstLaunchDate(): LocalDate? {
        return transaction {
            val dateStr = PluginMeta.selectAll().where { key.eq("first_launch_day") }.first()[value]
            LocalDate.parse(dateStr)
        }
    }
}
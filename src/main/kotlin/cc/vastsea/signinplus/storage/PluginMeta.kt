package cc.vastsea.signinplus.storage

import cc.vastsea.signinplus.SignInPlus.Companion.today
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.insertIgnore
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
    val key = varchar("key", 100)
    val value = text("value")

    override val primaryKey = PrimaryKey(key, name = "pk_plugin_meta")

    fun initFirstLaunchDay() {
        val day = today()
        transaction(DatabaseHelper.database) {
            PluginMeta.insertIgnore {
                it[key] = "first_launch_day"
                it[value] = day.toString()
            }
        }
    }

    fun getFirstLaunchDate(): LocalDate? {
        return transaction(DatabaseHelper.database) {
            PluginMeta.selectAll().where { key.eq("first_launch_day") }
                .firstOrNull()?.get(value)?.let(LocalDate::parse)
        }
    }
}

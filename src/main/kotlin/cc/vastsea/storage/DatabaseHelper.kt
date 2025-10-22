package cc.vastsea.storage

import cc.vastsea.SignInPlus
import org.jetbrains.exposed.sql.Database


object DatabaseHelper {
    var database: Database

    init {
        val url = SignInPlus.instance.config.getString("database.url")
        val type = SignInPlus.instance.config.getString("database.type")
        val username = SignInPlus.instance.config.getString("database.username")
        val password = SignInPlus.instance.config.getString("database.password")

        val driver = when (type) {
            "mysql" -> "com.mysql.cj.jdbc.Driver"
            "postgresql" -> "org.postgresql.Driver"
            "sqlite" -> "org.sqlite.JDBC"
            else -> throw IllegalArgumentException("Unsupported database type: $type")
        }
        val uri = when (type) {
            "mysql" -> "jdbc:mysql://$url"
            "postgresql" -> "jdbc:postgresql://$url"
            "sqlite" -> "jdbc:sqlite:plugins/SignInPlus/$url"
            else -> throw IllegalArgumentException("Unsupported database type: $type")
        }

        database = Database.connect(uri, driver = driver, user = username!!, password = password!!)
    }
}
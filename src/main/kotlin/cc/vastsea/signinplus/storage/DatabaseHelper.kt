package cc.vastsea.signinplus.storage

import cc.vastsea.signinplus.SignInPlus
import org.jetbrains.exposed.sql.Database
import java.io.File
import java.sql.DriverManager
import java.sql.SQLException


object DatabaseHelper {
    var database: Database

    init {
        val cfg = SignInPlus.instance.config
        val typeRaw = cfg.getString("database.type")?.lowercase() ?: "sqlite"
        val rawUrl = cfg.getString("database.url") ?: ""
        val username = cfg.getString("database.username") ?: ""
        val password = cfg.getString("database.password") ?: ""

        val driver = when (typeRaw) {
            "mysql" -> "com.mysql.cj.jdbc.Driver"
            "postgresql" -> "org.postgresql.Driver"
            "sqlite" -> "org.sqlite.JDBC"
            else -> throw IllegalArgumentException("Unsupported database type: $typeRaw")
        }

        try { Class.forName(driver) } catch (_: Throwable) {}

        val uri = when (typeRaw) {
            "sqlite" -> ensureSqlite(rawUrl)
            "mysql" -> ensureMysql(rawUrl, username, password)
            "postgresql" -> ensurePostgres(rawUrl, username, password)
            else -> throw IllegalArgumentException("Unsupported database type: $typeRaw")
        }

        database = Database.connect(uri, driver = driver, user = username, password = password)
    }

    private fun ensureSqlite(rawUrl: String): String {
        val dbFileName = if (rawUrl.isBlank() || rawUrl.contains(":")) "signinplus.db" else rawUrl
        val dir = File("plugins/SignInPlus")
        if (!dir.exists()) dir.mkdirs()
        val sqlitePath = "plugins/SignInPlus/$dbFileName"
        val f = File(sqlitePath)
        if (!f.exists()) {
            try { f.createNewFile() } catch (_: Throwable) {}
            SignInPlus.instance.logger.info("SQLite database initialized: $sqlitePath")
        }
        return "jdbc:sqlite:$sqlitePath"
    }

    private fun parseParts(rawUrl: String, defaultPort: String): Triple<String, String, String> {
        val main = rawUrl.substringBefore("?")
        val hostPort = main.substringBefore("/", "")
        val dbName = main.substringAfter("/", "").ifBlank { "signinplus" }
        val host = hostPort.substringBefore(":").ifBlank { "127.0.0.1" }
        val port = hostPort.substringAfter(":", defaultPort).ifBlank { defaultPort }
        return Triple(host, port, dbName)
    }

    private fun ensureMysql(rawUrl: String, user: String, pass: String): String {
        val params = rawUrl.substringAfter("?", "")
        val (host, port, db) = parseParts(rawUrl, "3306")
        val uri = buildString {
            append("jdbc:mysql://").append(host).append(":").append(port).append("/").append(db)
            if (params.isNotBlank()) append("?").append(params)
        }
        try {
            val adminUri = "jdbc:mysql://$host:$port/"
            DriverManager.getConnection(adminUri, user, pass).use { conn ->
                val rs = conn.createStatement().executeQuery(
                    "SELECT SCHEMA_NAME FROM INFORMATION_SCHEMA.SCHEMATA WHERE SCHEMA_NAME = '$db'"
                )
                if (!rs.next()) {
                    SignInPlus.instance.logger.info("Creating MySQL database: $db")
                    conn.createStatement().executeUpdate("CREATE DATABASE `$db` CHARACTER SET utf8mb4")
                }
            }
        } catch (e: SQLException) {
            SignInPlus.instance.logger.warning("MySQL database preflight failed: ${e.message}")
        } catch (t: Throwable) {
            SignInPlus.instance.logger.warning("MySQL preflight unexpected error: ${t.message}")
        }
        return uri
    }

    private fun ensurePostgres(rawUrl: String, user: String, pass: String): String {
        val params = rawUrl.substringAfter("?", "")
        val (host, port, db) = parseParts(rawUrl, "5432")
        val uri = buildString {
            append("jdbc:postgresql://").append(host).append(":").append(port).append("/").append(db)
            if (params.isNotBlank()) append("?").append(params)
        }
        try {
            val adminUri = "jdbc:postgresql://$host:$port/postgres"
            DriverManager.getConnection(adminUri, user, pass).use { conn ->
                val rs = conn.createStatement().executeQuery(
                    "SELECT 1 FROM pg_database WHERE datname = '$db'"
                )
                if (!rs.next()) {
                    SignInPlus.instance.logger.info("Creating PostgreSQL database: $db")
                    conn.createStatement().executeUpdate("CREATE DATABASE \"$db\"")
                }
            }
        } catch (e: SQLException) {
            SignInPlus.instance.logger.warning("PostgreSQL database preflight failed: ${e.message}")
        } catch (t: Throwable) {
            SignInPlus.instance.logger.warning("PostgreSQL preflight unexpected error: ${t.message}")
        }
        return uri
    }
}
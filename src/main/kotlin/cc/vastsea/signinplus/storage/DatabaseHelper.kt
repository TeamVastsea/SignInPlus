package cc.vastsea.signinplus.storage

import cc.vastsea.signinplus.SignInPlus
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.jetbrains.exposed.sql.Database
import java.io.File
import java.sql.Connection

object DatabaseHelper {
    lateinit var database: Database
        private set

    private var dataSource: HikariDataSource? = null

    fun init() {
        val cfg = SignInPlus.instance.config
        val type = cfg.getString("database.type")?.lowercase() ?: "sqlite"
        val rawUrl = cfg.getString("database.url") ?: ""
        val username = System.getenv("SIGNINPLUS_DB_USERNAME")
            ?: cfg.getString("database.username").orEmpty()
        val password = System.getenv("SIGNINPLUS_DB_PASSWORD")
            ?: cfg.getString("database.password").orEmpty()

        val driver = when (type) {
            "mysql" -> "com.mysql.cj.jdbc.Driver"
            "postgresql" -> "org.postgresql.Driver"
            "sqlite" -> "org.sqlite.JDBC"
            else -> throw IllegalArgumentException("Unsupported database type: $type")
        }
        val jdbcUrl = when (type) {
            "sqlite" -> sqliteUrl(rawUrl)
            "mysql" -> remoteUrl("mysql", rawUrl, 3306)
            "postgresql" -> remoteUrl("postgresql", rawUrl, 5432)
            else -> error("Unsupported database type: $type")
        }

        val poolSize = if (type == "sqlite") 1 else cfg.getInt("database.pool_size", 10).coerceIn(1, 50)
        val timeoutMs = cfg.getLong("database.connection_timeout_ms", 10_000L).coerceIn(1_000L, 60_000L)
        val hikariConfig = HikariConfig().apply {
            this.jdbcUrl = jdbcUrl
            driverClassName = driver
            this.username = username
            this.password = password
            maximumPoolSize = poolSize
            minimumIdle = if (type == "sqlite") 1 else 0
            connectionTimeout = timeoutMs
            validationTimeout = timeoutMs.coerceAtMost(5_000L)
            initializationFailTimeout = timeoutMs
            poolName = "SignInPlus-$type"
        }

        val newDataSource = HikariDataSource(hikariConfig)
        try {
            newDataSource.connection.use { connection ->
                check(connection.isValid((timeoutMs / 1_000L).coerceAtLeast(1L).toInt())) {
                    "Database connection validation failed"
                }
            }
            val oldDataSource = dataSource
            database = Database.connect(newDataSource)
            dataSource = newDataSource
            oldDataSource?.close()
        } catch (t: Throwable) {
            newDataSource.close()
            throw t
        }
    }

    fun close() {
        dataSource?.close()
        dataSource = null
    }

    internal fun useDatabaseForTests(value: Database) {
        database = value
    }

    internal fun lockSchemaInitialization(connection: Connection) {
        if (!connection.metaData.databaseProductName.contains("postgresql", ignoreCase = true)) return
        connection.prepareStatement("SELECT pg_advisory_xact_lock(?)").use { statement ->
            statement.setLong(1, SCHEMA_LOCK_ID)
            statement.executeQuery().use { it.next() }
        }
    }

    private fun sqliteUrl(rawUrl: String): String {
        val folder = SignInPlus.instance.dataFolder.canonicalFile
        if (!folder.exists() && !folder.mkdirs()) {
            error("Unable to create plugin data folder: ${folder.path}")
        }
        val fileName = rawUrl.ifBlank { "signinplus.db" }
        require(!fileName.startsWith("jdbc:") && !fileName.contains('\u0000')) {
            "database.url must be a relative SQLite file name"
        }
        val file = File(folder, fileName).canonicalFile
        require(file.toPath().startsWith(folder.toPath())) {
            "SQLite database must stay inside the SignInPlus data folder"
        }
        file.parentFile?.let { parent ->
            if (!parent.exists() && !parent.mkdirs()) error("Unable to create SQLite database directory")
        }
        return "jdbc:sqlite:${file.path}"
    }

    private fun remoteUrl(scheme: String, rawUrl: String, defaultPort: Int): String {
        val withoutJdbc = rawUrl.removePrefix("jdbc:$scheme://")
        val main = withoutJdbc.substringBefore("?")
        val params = withoutJdbc.substringAfter("?", "")
        val hostPort = main.substringBefore("/")
        val databaseName = main.substringAfter("/", "signinplus").ifBlank { "signinplus" }
        val host = hostPort.substringBefore(":").ifBlank { "127.0.0.1" }
        val port = hostPort.substringAfter(":", defaultPort.toString()).toIntOrNull()
            ?.takeIf { it in 1..65535 }
            ?: throw IllegalArgumentException("Invalid $scheme database port")

        require(host.matches(Regex("[A-Za-z0-9._:-]+"))) { "Invalid $scheme database host" }
        require(databaseName.matches(Regex("[A-Za-z0-9_]+"))) { "Invalid $scheme database name" }

        return buildString {
            append("jdbc:").append(scheme).append("://")
            append(host).append(":").append(port).append("/").append(databaseName)
            if (params.isNotBlank()) append("?").append(params)
        }
    }

    private const val SCHEMA_LOCK_ID = 0x5349474E494E504CL
}

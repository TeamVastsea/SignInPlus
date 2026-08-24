package cc.vastsea.signinplus

import cc.vastsea.signinplus.storage.*
import cc.vastsea.signinplus.locale.Localization
import cc.vastsea.signinplus.web.WebApiServer
import org.bukkit.plugin.java.JavaPlugin
import org.jetbrains.exposed.sql.SchemaUtils.create
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.logging.Level

class SignInPlus : JavaPlugin() {
    private var webServer: WebApiServer? = null
    lateinit var rewardExecutor: cc.vastsea.signinplus.rewards.RewardExecutor

    override fun onEnable() {
        // 确保所有资源文件存在
        ensureResources()

        instance = this

        // 初始化本地化 (从 config.yml 中读取 "locale"，默认 en_US)
        localization = Localization(instance)
        val locale = config.getString("locale") ?: "en_US"
        localization.load(locale)

        // 初始化存储
        loadZoneId()
        
        // 初始化数据库连接
        initializeStorage()

        // 奖励执行器
        rewardExecutor = cc.vastsea.signinplus.rewards.RewardExecutor(this)

        // 注册命令
        getCommand("signinplus")?.setExecutor(SignInPlusCommand(this))
        getCommand("signinplus")?.tabCompleter = SignInPlusCommand(this)

        // 登录自动签到监听器
        server.pluginManager.registerEvents(cc.vastsea.signinplus.listeners.LoginAutoCheckInListener(this), this)

        // 注册 PlaceholderAPI 扩展（如果存在）
        if (server.pluginManager.getPlugin("PlaceholderAPI") != null) {
            try {
                SignInPlusExpansion(this).register()
                logger.info("PlaceholderAPI Expansion Registered")
            } catch (t: Throwable) {
                logger.warning("Register Placeholder Expansion Failed: ${t.message}")
            }
        }

        // 启动 Web API（仅查询接口）
        startWebApiIfEnabled()

        logger.info("SignInPlus Enabled")
    }

    override fun onDisable() {
        try {
            webServer?.stop()
            webServer = null
        } finally {
            DatabaseHelper.close()
        }
        logger.info("SignInPlus Disabled")
    }

    fun reloadAll(): Boolean {
        return try {
            reloadAllUnsafe()
            true
        } catch (t: Throwable) {
            logger.log(Level.SEVERE, "SignInPlus reload failed; disabling the plugin to avoid partial state", t)
            server.pluginManager.disablePlugin(this)
            false
        }
    }

    private fun reloadAllUnsafe() {
        webServer?.stop()
        webServer = null

        // 重载前先确保资源文件存在（应对用户误删的情况）
        ensureResources()
        reloadConfig()
        loadZoneId()
        // 重新加载本地化（以便管理员修改 data-folder 下的 lang 文件或更改 config.locale）
        val newLocale = config.getString("locale") ?: localization.locale
        localization.load(newLocale)
        
        // 重新初始化数据库（如果文件被删，这里会重新创建；如果配置变更，这里会应用新配置）
        initializeStorage()

        // 重新创建奖励执行器，应用最新配置（如消息前缀、奖励表）
        rewardExecutor = cc.vastsea.signinplus.rewards.RewardExecutor(this)

        // 重新绑定命令执行器以应用最新前缀等配置
        getCommand("signinplus")?.let { cmd ->
            val executor = SignInPlusCommand(this)
            cmd.setExecutor(executor)
            cmd.tabCompleter = executor
        }

        // 重启 Web API（根据新配置开关）
        startWebApiIfEnabled()
    }

    private fun initializeStorage() {
        DatabaseHelper.init()
        try {
            transaction(DatabaseHelper.database) {
                DatabaseHelper.lockSchemaInitialization(connection.connection as java.sql.Connection)
                create(
                    Checkins,
                    ClaimedRewards,
                    CorrectionSlips,
                    PluginMeta,
                    PlayerProfiles,
                    Points,
                    SpecialDateClaims,
                )
            }
            PluginMeta.initFirstLaunchDay()
        } catch (t: Throwable) {
            DatabaseHelper.close()
            throw t
        }
    }

    private fun startWebApiIfEnabled() {
        val web = config.getConfigurationSection("web_api") ?: return
        val enabled = web.getBoolean("enable_web_api")
        if (!enabled) return

        val address = web.getString("web_api_address")?.trim().takeUnless { it.isNullOrEmpty() } ?: "127.0.0.1"
        val port = web.getInt("web_api_port")
        val endpoint = web.getString("web_api_endpoint") ?: "/api"
        val apiKey = web.getString("api_key")?.trim().orEmpty()
        if (apiKey.length < 24) {
            logger.severe("Web API is enabled but web_api.api_key is missing or shorter than 24 characters; server was not started")
            return
        }
        if (port !in 1..65_535) {
            logger.severe("Web API is enabled but web_api.web_api_port is outside 1..65535; server was not started")
            return
        }
        val requestsPerMinute = web.getInt("requests_per_minute", 60).coerceIn(1, 10_000)

        var candidate: WebApiServer? = null
        try {
            candidate = WebApiServer(this, address, port, endpoint, apiKey, requestsPerMinute)
            candidate.start()
            webServer = candidate
            logger.info("Web API Launched: http://$address:$port$endpoint")
        } catch (t: Throwable) {
            candidate?.stop()
            logger.severe("Web API failed to start: ${t.message}")
        }
    }

    private fun loadZoneId() {
        val configured = config.getString("timezone") ?: "Asia/Shanghai"
        zoneId = runCatching { ZoneId.of(configured) }.getOrElse {
            logger.warning("Invalid timezone '$configured'; falling back to Asia/Shanghai")
            ZoneId.of("Asia/Shanghai")
        }
    }

    private fun ensureResources() {
        // 检查 config.yml 是否已存在（用于判断是否为首次安装/初始化）
        val configExists = java.io.File(dataFolder, "config.yml").exists()
        
        // 1. 核心配置：如果不存在则创建
        if (!configExists) {
            saveDefaultConfig()
        }
        
        // 2. 语言文件：始终检查并补全（防止缺失）
        val langFiles = listOf("lang/en_US.yml", "lang/zh_CN.yml")
        for (path in langFiles) {
            val file = java.io.File(dataFolder, path)
            if (!file.exists()) {
                saveResource(path, false)
                logger.info("Created default language file: $path")
            }
        }
        
        // 3. 中文配置参考：仅在首次初始化（即 config.yml 之前不存在）时释放
        // 这样后续重启服务器时，即使用户删除了 config_zh_CN.yml，也不会再次生成
        if (!configExists) {
            val cnConfigPath = "config_zh_CN.yml"
            val cnConfigFile = java.io.File(dataFolder, cnConfigPath)
            if (!cnConfigFile.exists()) {
                saveResource(cnConfigPath, false)
                logger.info("Created reference config: $cnConfigPath")
            }
        }
        
        // 确保内存中的配置与磁盘文件同步
        reloadConfig()
    }

    companion object {
        lateinit var instance: SignInPlus
            private set

        lateinit var zoneId: ZoneId
            private set

        lateinit var localization: Localization
            private set

        fun today(): LocalDate = LocalDate.now(zoneId)
        fun now(): LocalTime = LocalTime.now(zoneId)

        internal fun setZoneIdForTests(value: ZoneId) {
            zoneId = value
        }
    }
}

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
        val tz = config.getString("timezone") ?: "Asia/Shanghai"
        zoneId = ZoneId.of(tz)
        
        // 初始化数据库连接
        DatabaseHelper.init()
        transaction(DatabaseHelper.database) { create(Checkins, ClaimedRewards, CorrectionSlips, PluginMeta, Points, SpecialDateClaims) }
        PluginMeta.initFirstLaunchDay()

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
        // 停止 Web API
        webServer?.stop()
        webServer = null
        logger.info("SignInPlus Disabled")
    }

    fun reloadAll() {
        // 重载前先确保资源文件存在（应对用户误删的情况）
        ensureResources()
        reloadConfig()
        // 重新加载本地化（以便管理员修改 data-folder 下的 lang 文件或更改 config.locale）
        val newLocale = config.getString("locale") ?: localization.locale
        localization.load(newLocale)
        
        // 重新初始化数据库（如果文件被删，这里会重新创建；如果配置变更，这里会应用新配置）
        DatabaseHelper.init()
        // 确保表结构存在
        transaction(DatabaseHelper.database) { create(Checkins, ClaimedRewards, CorrectionSlips, PluginMeta, Points, SpecialDateClaims) }
        PluginMeta.initFirstLaunchDay()

        // 重新创建奖励执行器，应用最新配置（如消息前缀、奖励表）
        rewardExecutor = cc.vastsea.signinplus.rewards.RewardExecutor(this)

        // 重新绑定命令执行器以应用最新前缀等配置
        getCommand("signinplus")?.let { cmd ->
            val executor = SignInPlusCommand(this)
            cmd.setExecutor(executor)
            cmd.tabCompleter = executor
        }

        // 重启 Web API（根据新配置开关）
        webServer?.stop()
        webServer = null
        startWebApiIfEnabled()
    }

    private fun startWebApiIfEnabled() {
        val web = config.getConfigurationSection("web_api") ?: return
        val enabled = web.getBoolean("enable_web_api")
        if (!enabled) return

        val address = web.getString("web_api_address") ?: "0.0.0.0"
        val port = web.getInt("web_api_port")
        val endpoint = web.getString("web_api_endpoint") ?: "/api"

        webServer = WebApiServer(address, port, endpoint)
        webServer?.start()
        logger.info("Web API Launched: http://$address:$port$endpoint")
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
    }
}
package cc.vastsea

import cc.vastsea.storage.*
import cc.vastsea.web.WebApiServer
import org.bukkit.plugin.java.JavaPlugin
import org.jetbrains.exposed.sql.SchemaUtils.create
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class SignInPlus : JavaPlugin() {
    private var webServer: WebApiServer? = null
    lateinit var rewardExecutor: cc.vastsea.rewards.RewardExecutor

    override fun onEnable() {
        // 保存默认配置
        saveDefaultConfig()
        instance = this

        // 初始化存储
        val tz = config.getString("timezone") ?: "Asia/Shanghai"
        zoneId = ZoneId.of(tz)
        transaction(DatabaseHelper.database) { create(Checkins, ClaimedRewards, CorrectionSlips, PluginMeta, Points, SpecialDateClaims) }
        PluginMeta.initFirstLaunchDay()

        // 奖励执行器
        rewardExecutor = cc.vastsea.rewards.RewardExecutor(this)

        // 注册命令
        val cmd = getCommand("signinplus")
        if (cmd != null) {
            val executor = SignInPlusCommand(this)
            cmd.setExecutor(executor)
            cmd.tabCompleter = executor
        } else {
            logger.warning("未找到命令: /signinplus")
        }

        // 登录自动签到监听器
        server.pluginManager.registerEvents(cc.vastsea.listeners.LoginAutoCheckInListener(this), this)

        // 注册 PlaceholderAPI 扩展（如果存在）
        if (server.pluginManager.getPlugin("PlaceholderAPI") != null) {
            try {
                SignInPlusExpansion(this).register()
                logger.info("PlaceholderAPI 扩展已注册")
            } catch (t: Throwable) {
                logger.warning("注册 Placeholder 扩展失败: ${t.message}")
            }
        }

        // 启动 Web API（仅查询接口）
        startWebApiIfEnabled()

        logger.info("SignInPlus 已启用")
    }

    override fun onDisable() {
        // 停止 Web API
        webServer?.stop()
        webServer = null
        logger.info("SignInPlus 已禁用")
    }

    fun reloadAll() {
        reloadConfig()
        // 重新创建奖励执行器，应用最新配置（如消息前缀、奖励表）
        rewardExecutor = cc.vastsea.rewards.RewardExecutor(this)

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
        val web = config.getConfigurationSection("web_api")
        val enabled = web?.getBoolean("enable_web_api") ?: false
        if (!enabled) return

        val address = web?.getString("web_api_address") ?: "0.0.0.0"
        val port = web?.getInt("web_api_port") ?: 7999
        val endpoint = web?.getString("web_api_endpoint") ?: "/api"

        webServer = WebApiServer(address, port, endpoint)
        webServer?.start()
        logger.info("Web API 已启动: http://$address:$port$endpoint")
    }

    companion object {
        lateinit var instance: SignInPlus
            private set

        lateinit var zoneId: ZoneId
            private set

        fun today(): LocalDate = LocalDate.now(zoneId)
        fun now(): LocalTime = LocalTime.now(zoneId)
    }
}
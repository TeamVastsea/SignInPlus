package cc.vastsea.signinplus.listeners

import cc.vastsea.signinplus.SignInPlus
import cc.vastsea.signinplus.storage.Checkins
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent

class LoginAutoCheckInListener(private val plugin: SignInPlus) : Listener {
    @EventHandler
    fun onJoin(e: PlayerJoinEvent) {
        // 如果已经签到，无需执行任何自动操作
        if (Checkins.isSignedIn(e.player.uniqueId)) {
            return
        }

        // 读取新配置 on_login_action
        val action = plugin.config.getString("on_login_action")?.lowercase()

        // 兼容旧配置 (如果新配置不存在)
        if (action == null) {
            val oldEnable = plugin.config.getConfigurationSection("auto_check_in_on_login")?.getBoolean("enable") ?: false
            if (oldEnable) {
                Checkins.signInToday(e.player.uniqueId)
                plugin.rewardExecutor.onSignedIn(e.player.uniqueId)
            }
            return
        }

        when (action) {
            "signin" -> {
                Checkins.signInToday(e.player.uniqueId)
                plugin.rewardExecutor.onSignedIn(e.player.uniqueId)
            }
            "open_gui" -> {
                // 延迟打开 GUI，确保玩家完全加入
                plugin.server.scheduler.runTaskLater(plugin, Runnable {
                    cc.vastsea.signinplus.gui.SignInGui.open(e.player)
                }, 20L)
            }
            "none" -> {
                // Do nothing
            }
        }
    }
}
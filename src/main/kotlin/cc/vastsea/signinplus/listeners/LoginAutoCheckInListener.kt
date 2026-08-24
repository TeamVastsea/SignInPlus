package cc.vastsea.signinplus.listeners

import cc.vastsea.signinplus.SignInPlus
import cc.vastsea.signinplus.storage.Checkins
import cc.vastsea.signinplus.storage.PlayerProfiles
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent

class LoginAutoCheckInListener(private val plugin: SignInPlus) : Listener {
    @EventHandler
    fun onJoin(e: PlayerJoinEvent) {
        val player = e.player
        if (plugin.config.getBoolean("identity.require_stable_uuid", false) &&
            PlayerProfiles.isNameDerivedOfflineUuid(player.uniqueId, player.name)
        ) {
            plugin.logger.severe(
                "Rejected name-derived offline UUID for ${player.name}; check Velocity modern forwarding/UniversalAuth"
            )
            player.kickPlayer(SignInPlus.localization.get("commands.unstable_uuid"))
            return
        }

        PlayerProfiles.remember(player.uniqueId, player.name)

        val action = plugin.config.getString("on_login_action")?.lowercase()
        if (action == "none") return

        // 如果已经签到，无需执行任何自动操作
        if (Checkins.isSignedIn(player.uniqueId)) {
            return
        }

        // 读取新配置 on_login_action
        // 兼容旧配置 (如果新配置不存在)
        if (action == null) {
            val oldEnable = plugin.config.getConfigurationSection("auto_check_in_on_login")?.getBoolean("enable") ?: false
            if (oldEnable) {
                if (Checkins.signInToday(player.uniqueId)) {
                    plugin.rewardExecutor.onSignedIn(player.uniqueId)
                }
            }
            return
        }

        when (action) {
            "signin" -> {
                if (Checkins.signInToday(player.uniqueId)) {
                    plugin.rewardExecutor.onSignedIn(player.uniqueId)
                }
            }
            "open_gui" -> {
                // 延迟打开 GUI，确保玩家完全加入
                plugin.server.scheduler.runTaskLater(plugin, Runnable {
                    if (player.isOnline) cc.vastsea.signinplus.gui.SignInGui.open(player)
                }, 20L)
            }
            "none" -> {
                // Do nothing
            }
        }
    }
}

package cc.vastsea.listeners

import cc.vastsea.SignInPlus
import cc.vastsea.storage.Checkins
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent

class LoginAutoCheckInListener(private val plugin: SignInPlus) : Listener {
    @EventHandler
    fun onJoin(e: PlayerJoinEvent) {
        val enable = plugin.config.getConfigurationSection("auto_check_in_on_login")?.getBoolean("enable") ?: false
        if (!enable) return
        Checkins.signInToday(e.player.uniqueId)
        plugin.rewardExecutor.onSignedIn(e.player.uniqueId)
    }
}
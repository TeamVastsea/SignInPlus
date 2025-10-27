package cc.vastsea.rewards

import cc.vastsea.SignInPlus
import cc.vastsea.util.ConsoleColor

class DebugLogger(private val plugin: SignInPlus) {
    private val isDebug = plugin.config.getBoolean("debug", false)

    fun info(message: String) {
        if (isDebug) {
            plugin.logger.info(ConsoleColor.colorizeAmpersand("&e[Debug] &r$message"))
        }
    }
}
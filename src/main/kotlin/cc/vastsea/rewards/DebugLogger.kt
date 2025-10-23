package cc.vastsea.rewards

import cc.vastsea.SignInPlus

class DebugLogger(private val plugin: SignInPlus) {
    private val isDebug = plugin.config.getBoolean("debug", false)

    private fun colorizeForConsole(message: String): String {
        val colorMap = mapOf(
            "&0" to "\u001B[0;30m", // Black
            "&1" to "\u001B[0;34m", // Dark Blue
            "&2" to "\u001B[0;32m", // Dark Green
            "&3" to "\u001B[0;36m", // Dark Aqua
            "&4" to "\u001B[0;31m", // Dark Red
            "&5" to "\u001B[0;35m", // Dark Purple
            "&6" to "\u001B[0;33m", // Gold
            "&7" to "\u001B[0;37m", // Gray
            "&8" to "\u001B[0;90m", // Dark Gray
            "&9" to "\u001B[0;94m", // Blue
            "&a" to "\u001B[0;92m", // Green
            "&b" to "\u001B[0;96m", // Aqua
            "&c" to "\u001B[0;91m", // Red
            "&d" to "\u001B[0;95m", // Light Purple
            "&e" to "\u001B[0;93m", // Yellow
            "&f" to "\u001B[0;97m", // White
            "&r" to "\u001B[0m"      // Reset
        )
        var coloredMessage = message
        for ((key, value) in colorMap) {
            coloredMessage = coloredMessage.replace(key, value)
        }
        return coloredMessage + "\u001B[0m" // Ensure reset at the end
    }

    fun info(message: String) {
        if (isDebug) {
            plugin.logger.info(colorizeForConsole("&e[Debug] &r$message"))
        }
    }
}
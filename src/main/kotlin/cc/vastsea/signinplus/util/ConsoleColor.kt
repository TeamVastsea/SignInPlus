package cc.vastsea.signinplus.util

/**
 * Console ANSI colorizer for Spigot-style ampersand codes.
 * Converts sequences like "&a" to ANSI escape codes for readable colored logs.
 * Only affects console output; player messages should use '§' codes directly.
 */
object ConsoleColor {
    private val colorMap = mapOf(
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

    /**
     * Colorize a message containing ampersand color codes for console output.
     * Appends a reset at the end to avoid bleeding colors.
     */
    fun colorizeAmpersand(message: String): String {
        var out = message
        for ((key, value) in colorMap) {
            out = out.replace(key, value)
        }
        return out + "\u001B[0m"
    }
}
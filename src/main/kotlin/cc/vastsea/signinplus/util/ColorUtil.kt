package cc.vastsea.signinplus.util

object ColorUtil {
    /**
     * 将 Spigot 风格的 & 颜色码转换为 §，保留已有的 §。
     */
    fun ampersandToSection(input: String?): String {
        if (input == null) return ""
        return input.replace('&', '§')
    }
}
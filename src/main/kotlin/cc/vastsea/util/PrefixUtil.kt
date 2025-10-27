package cc.vastsea.util

import org.bukkit.plugin.java.JavaPlugin

object PrefixUtil {
    private const val DEFAULT_PREFIX = "§7[§a签到Plus§7] "

    /**
     * 从配置读取 `message_prefix`，并将 `&` 颜色码转换为 `§`。
     * 对于缺省配置，返回内置默认前缀。
     */
    fun fromConfig(plugin: JavaPlugin): String {
        val raw = plugin.config.getString("message_prefix") ?: DEFAULT_PREFIX
        return raw.replace('&', '§')
    }
}
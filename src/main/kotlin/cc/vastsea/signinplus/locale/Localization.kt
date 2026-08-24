package cc.vastsea.signinplus.locale

import cc.vastsea.signinplus.util.ColorUtil
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.nio.charset.StandardCharsets

class Localization(private val plugin: JavaPlugin) {
    var locale: String = "en_US"
    private val messages: MutableMap<String, String> = mutableMapOf()

    fun load(newLocale: String) {
        val folder = File(plugin.dataFolder, "lang")
        if (!folder.exists()) folder.mkdirs()
        val resourceName = "lang/$newLocale.yml"
        val file = File(folder, "$newLocale.yml")
        if (!file.exists()) plugin.saveResource(resourceName, false)
        messages.clear()

        // Load bundled messages first so upgrades can add keys without overwriting administrator edits.
        plugin.getResource(resourceName)?.reader(StandardCharsets.UTF_8)?.use { reader ->
            val defaults = YamlConfiguration.loadConfiguration(reader)
            for (key in defaults.getKeys(true)) {
                if (defaults.isString(key)) defaults.getString(key)?.let { messages[key] = it }
            }
        }

        val config = YamlConfiguration.loadConfiguration(file)
        for (key in config.getKeys(true)) {
            if (config.isString(key)) config.getString(key)?.let { messages[key] = it }
        }
        locale = newLocale
    }

    /**
     * 通过键获取本地化消息（点分隔）。占位符会将 {name} 替换为提供的值。
     */
    fun get(key: String, placeholders: Map<String, String>? = null): String {
        var result = messages[key] ?: "<$key>"
        if (placeholders != null) {
            for ((k, v) in placeholders) {
                result = result.replace("{$k}", v)
            }
        }
        // 统一在返回时进行 & 到 § 的转换
        return ColorUtil.ampersandToSection(result)
    }
}

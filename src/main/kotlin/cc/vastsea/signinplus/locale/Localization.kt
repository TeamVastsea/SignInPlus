package cc.vastsea.signinplus.locale

import cc.vastsea.signinplus.util.ColorUtil
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.java.JavaPlugin
import java.io.File

class Localization(private val plugin: JavaPlugin) {
    var locale: String = "en_US"
    private val messages: MutableMap<String, String> = mutableMapOf()

    fun load(newLocale: String) {
        val folder = File(plugin.dataFolder, "lang")
        if (!folder.exists()) folder.mkdirs()
        val resourceName = "lang/$newLocale.yml"
        val file = File(folder, "$newLocale.yml")
        if (!file.exists()) plugin.saveResource(resourceName, false)
        val config = YamlConfiguration.loadConfiguration(file)
        messages.clear()
        for (key in config.getKeys(true)) {
            if (config.isString(key)) {
                val value = config.getString(key) ?: continue
                // 保留原始内容，转换在 get() 时进行，支持 & 与 § 并存
                messages[key] = value
            }
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

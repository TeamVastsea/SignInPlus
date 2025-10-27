package cc.vastsea.locale

import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.io.InputStreamReader

class Localization(private val plugin: JavaPlugin) {
    private val messages: MutableMap<String, String> = mutableMapOf()
    var locale: String = "en_US"
        private set

    /** 加载指定语言 (如 "en_US")。优先使用插件数据目录下的外部文件；若不存在则使用打包资源。资源中的颜色码应使用 § 符号。 */
    fun load(locale: String) {
        this.locale = locale
        messages.clear()

        val langDir = File(plugin.dataFolder, "lang")
        if (!langDir.exists()) langDir.mkdirs()

        val external = File(langDir, "$locale.yml")

        val config = when {
            external.exists() -> {
                YamlConfiguration.loadConfiguration(external)
            }
            else -> {
                // 如果 jar 中存在资源，则复制到数据目录便于管理员修改，然后加载
                val resourcePath = "lang/$locale.yml"
                val stream = plugin.getResource(resourcePath)
                if (stream != null) {
                    try {
                        // 尝试保存一份到数据目录，方便编辑
                        plugin.saveResource(resourcePath, false)
                    } catch (ignored: IllegalArgumentException) {
                        // 若已存在会抛异常，忽略之
                    }
                    val reader = InputStreamReader(stream, Charsets.UTF_8)
                    YamlConfiguration.loadConfiguration(reader)
                } else {
                    plugin.logger.warning("Localization resource $resourcePath not found in jar and no external file present")
                    YamlConfiguration()
                }
            }
        }

        // 收集所有字符串键（递归），直接保留原始内容（§ 颜色码）
        for (key in config.getKeys(true)) {
            val value = config.getString(key) ?: continue
            messages[key] = value
        }

        plugin.logger.info("Localization loaded: $locale (${messages.size} messages)")
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
        return result
    }
}

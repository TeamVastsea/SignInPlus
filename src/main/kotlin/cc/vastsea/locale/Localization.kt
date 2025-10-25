package cc.vastsea.locale

import org.bukkit.ChatColor
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.io.InputStreamReader

/**
 * Simple localization loader that supports:
 * - loading lang files from plugin data folder (plugins/SignInPlus/lang/<locale>.yml)
 * - falling back to the embedded resource under /lang/<locale>.yml
 * - placeholder replacement using {name} style
 * - color code translation using & -> § (via ChatColor)
 */
class Localization(private val plugin: JavaPlugin) {
    private val messages: MutableMap<String, String> = mutableMapOf()
    var locale: String = "en_US"
        private set

    /** Load a locale (e.g. "en_US"). Will prefer external file under plugin data folder if present, else use bundled resource. */
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
                // If resource exists in jar, copy it to data folder for easy customization and load it.
                val resourcePath = "lang/$locale.yml"
                val stream = plugin.getResource(resourcePath)
                if (stream != null) {
                    try {
                        // try to save a copy to data folder so server admins can edit
                        plugin.saveResource(resourcePath, false)
                    } catch (ignored: IllegalArgumentException) {
                        // saveResource may throw if already exists — ignore
                    }
                    val reader = InputStreamReader(stream, Charsets.UTF_8)
                    YamlConfiguration.loadConfiguration(reader)
                } else {
                    plugin.logger.warning("Localization resource $resourcePath not found in jar and no external file present")
                    YamlConfiguration()
                }
            }
        }

        // collect all string keys (recursive)
        for (key in config.getKeys(true)) {
            val value = config.getString(key) ?: continue
            messages[key] = ChatColor.translateAlternateColorCodes('&', value)
        }

        plugin.logger.info("Localization loaded: $locale (${messages.size} messages)")
    }

    /**
     * Get a localized message by key (dot-separated). Example key: "msg.hello".
     * Placeholders map will replace {name} with the value provided.
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

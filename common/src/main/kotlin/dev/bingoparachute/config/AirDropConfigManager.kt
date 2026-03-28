package dev.bingoparachute.config

import kotlinx.serialization.json.Json
import org.slf4j.Logger
import java.nio.file.Files
import java.nio.file.Path

class AirDropConfigManager(
    private val configPath: Path,
    private val log: Logger,
) {
    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    @Volatile
    var config: AirDropConfig = AirDropConfig()
        private set

    fun load(): AirDropConfig {
        Files.createDirectories(configPath.parent)

        config = if (Files.exists(configPath)) {
            val rawConfig = Files.readString(configPath)
            json.decodeFromString(AirDropConfig.serializer(), rawConfig).also { loadedConfig ->
                val normalizedConfig = json.encodeToString(AirDropConfig.serializer(), loadedConfig)
                if (normalizedConfig != rawConfig) {
                    Files.writeString(configPath, normalizedConfig)
                    log.info("Updated config file with missing defaults at {}", configPath)
                }
            }
        } else {
            AirDropConfig().also {
                Files.writeString(configPath, json.encodeToString(AirDropConfig.serializer(), it))
            }
        }

        log.info(
            "Loaded config: enabled={}, mode={}, spawnHeight={}, pvpProtectionSeconds={}",
            config.enabled,
            config.mode,
            config.spawnHeight,
            config.pvpProtectionSeconds
        )

        return config
    }
}

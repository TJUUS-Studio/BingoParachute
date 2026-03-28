package dev.bingoparachute

import dev.bingoparachute.airdrop.AirDropRuntimeController
import dev.bingoparachute.config.AirDropConfigManager
import dev.bingoparachute.pvp.PvpProtectionController
import dev.bingoparachute.session.AirDropSessionManager
import dev.bingoparachute.bingo.BingoLifecycleBridge
import dev.bingoparachute.server.ServerEventBridge
import net.fabricmc.loader.api.FabricLoader
import net.fabricmc.api.ModInitializer
import net.minecraft.server.MinecraftServer
import org.slf4j.LoggerFactory

class BingoParachuteMod : ModInitializer {
    companion object {
        const val MOD_ID = "bingo-parachute"
        private val bingoModIds = setOf("yet-another-minecraft-bingo", "bingo")

        val log = LoggerFactory.getLogger(MOD_ID)
        var server: MinecraftServer? = null
            internal set
        lateinit var configManager: AirDropConfigManager
            private set
        lateinit var sessionManager: AirDropSessionManager
            private set
        lateinit var pvpProtectionController: PvpProtectionController
            private set
        lateinit var runtimeController: AirDropRuntimeController
            private set
        lateinit var bingoBridge: BingoLifecycleBridge
            private set
        lateinit var serverEventBridge: ServerEventBridge
            private set
    }

    override fun onInitialize() {
        val configPath = FabricLoader.getInstance().configDir.resolve("bingo-parachute.json")

        configManager = AirDropConfigManager(
            configPath = configPath,
            log = log
        )
        sessionManager = AirDropSessionManager(log)
        pvpProtectionController = PvpProtectionController(sessionManager)
        runtimeController = AirDropRuntimeController(sessionManager, configManager, log)
        bingoBridge = BingoLifecycleBridge(sessionManager, runtimeController)
        serverEventBridge = ServerEventBridge(sessionManager, runtimeController, pvpProtectionController)

        configManager.load()
        sessionManager.setBingoDetected(isBingoPresent())
        bingoBridge.initialize()
        serverEventBridge.initialize()

        log.info(
            "Initialized Bingo Parachute for Minecraft {} (bingoDetected={})",
            BuildInfo.minecraftVersion,
            sessionManager.isBingoDetected
        )
    }

    private fun isBingoPresent(): Boolean {
        val loader = FabricLoader.getInstance()
        return bingoModIds.any(loader::isModLoaded)
    }
}

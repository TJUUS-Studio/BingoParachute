package dev.bingoparachute.airdrop

import dev.bingoparachute.config.AirDropConfig
import dev.bingoparachute.config.AirDropConfigManager
import dev.bingoparachute.session.AirDropSessionManager
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity
import org.slf4j.Logger

class AirDropRuntimeController(
    private val sessionManager: AirDropSessionManager,
    private val configManager: AirDropConfigManager,
    log: Logger,
) {
    private val batHandler = BatCarrierHandler(log)
    private val loadoutCustodian = PlayerLoadoutCustodian(ServerPlayerLoadoutAdapter())
    private val notificationAdapter = ServerPlayerNotificationAdapter()
    private val coordinator = AirDropRuntimeCoordinator(
        sessionManager = sessionManager,
        configManager = configManager,
        handlers = mapOf<AirDropConfig.CarrierMode, CarrierModeHandler<ServerPlayerEntity>>(
            AirDropConfig.CarrierMode.BAT to batHandler,
            AirDropConfig.CarrierMode.ELYTRA to FallbackCarrierHandler<ServerPlayerEntity>(
                mode = AirDropConfig.CarrierMode.ELYTRA,
                delegate = batHandler,
                log = log,
            ),
        ),
        loadoutCustodian = loadoutCustodian,
        notificationAdapter = notificationAdapter,
        log = log,
    )

    fun onServerTick(server: MinecraftServer) {
        coordinator.onTick(server.ticks.toLong(), server.playerManager::getPlayer)
    }

    fun onServerStopping(server: MinecraftServer?) {
        cleanup(server)
    }

    fun onGameReset(server: MinecraftServer?) {
        cleanup(server)
    }

    fun onGameEnded(server: MinecraftServer?) {
        cleanup(server)
    }

    fun onPlayerDisconnect(player: ServerPlayerEntity) {
        coordinator.cleanupPlayer(player.uuid, player)
    }

    fun onPlayerRespawn(player: ServerPlayerEntity) {
        coordinator.restorePlayerLoadout(player.uuid, player)
    }

    private fun cleanup(server: MinecraftServer?) {
        val activeServer = server ?: return
        coordinator.cleanup(activeServer.playerManager::getPlayer)
    }
}

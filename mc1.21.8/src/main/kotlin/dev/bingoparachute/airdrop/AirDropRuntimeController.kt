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
    private val elytraHandler = ElytraCarrierHandler(log)
    private val loadoutCustodian = PlayerLoadoutCustodian(ServerPlayerLoadoutAdapter())
    private val notificationAdapter = ServerPlayerNotificationAdapter()
    private val coordinator = AirDropRuntimeCoordinator(
        sessionManager = sessionManager,
        configManager = configManager,
        handlers = mapOf<AirDropConfig.CarrierMode, CarrierModeHandler<ServerPlayerEntity>>(
            AirDropConfig.CarrierMode.BAT to batHandler,
            AirDropConfig.CarrierMode.ELYTRA to elytraHandler,
        ),
        loadoutCustodian = loadoutCustodian,
        notificationAdapter = notificationAdapter,
        log = log,
    )

    fun onServerTick(server: MinecraftServer) {
        coordinator.onTick(server.ticks.toLong(), server.playerManager::getPlayer)
    }

    fun onServerStopping(server: MinecraftServer?) {
        cleanup(server, "server_stopping")
    }

    fun onGameReset(server: MinecraftServer?) {
        cleanup(server, "game_reset")
    }

    fun onGameEnded(server: MinecraftServer?) {
        cleanup(server, "game_ended")
    }

    fun onPlayerDisconnect(player: ServerPlayerEntity) {
        coordinator.cleanupPlayer(player.uuid, player, "player_disconnect")
    }

    fun onPlayerRespawn(oldPlayer: ServerPlayerEntity, newPlayer: ServerPlayerEntity) {
        coordinator.cleanupPlayer(oldPlayer.uuid, oldPlayer, "player_respawn", restoreLoadout = false)
        coordinator.restorePlayerLoadout(newPlayer.uuid, newPlayer)
    }

    private fun cleanup(server: MinecraftServer?, reason: String) {
        val activeServer = server ?: return
        coordinator.cleanup(activeServer.playerManager::getPlayer, reason)
    }
}

package dev.bingoparachute.airdrop

import dev.bingoparachute.config.AirDropConfig
import dev.bingoparachute.config.AirDropConfigManager
import dev.bingoparachute.session.AirDropSessionManager
import org.slf4j.Logger
import java.util.UUID

class AirDropRuntimeCoordinator<PlayerT>(
    private val sessionManager: AirDropSessionManager,
    private val configManager: AirDropConfigManager,
    private val handlers: Map<AirDropConfig.CarrierMode, CarrierModeHandler<PlayerT>>,
    private val loadoutCustodian: PlayerLoadoutCustodian<PlayerT, *>,
    private val log: Logger,
) {
    fun onTick(
        tick: Long,
        resolvePlayer: (UUID) -> PlayerT?,
    ) {
        val config = configManager.config
        if (!config.enabled) {
            return
        }

        val session = sessionManager.currentSession ?: return
        for ((playerUuid, state) in session.playerStates) {
            val player = resolvePlayer(playerUuid) ?: continue
            ensureLoadoutStored(player, state)
            handlerFor(state.mode).tick(
                player = player,
                session = session,
                state = state,
                tick = tick,
                config = config,
            )
            maybeRestoreLoadout(player, state)
        }
    }

    fun cleanup(
        resolvePlayer: (UUID) -> PlayerT?,
    ) {
        val session = sessionManager.currentSession ?: return
        for ((playerUuid, state) in session.playerStates) {
            val player = resolvePlayer(playerUuid) ?: continue
            handlerFor(state.mode).cleanup(player, state)
            maybeRestoreLoadout(player, state)
        }
        loadoutCustodian.clearSnapshots()
    }

    fun cleanupPlayer(
        playerUuid: UUID,
        player: PlayerT,
    ) {
        val state = sessionManager.currentSession?.playerStates?.get(playerUuid) ?: return
        handlerFor(state.mode).cleanup(player, state)
        maybeRestoreLoadout(player, state)
    }

    fun restorePlayerLoadout(
        playerUuid: UUID,
        player: PlayerT,
    ): Boolean {
        val state = sessionManager.currentSession?.playerStates?.get(playerUuid) ?: return false
        if (!state.loadoutStored || state.loadoutRestored) {
            return false
        }
        if (!loadoutCustodian.hasSnapshot(playerUuid)) {
            return false
        }
        state.phase = dev.bingoparachute.session.AirDropPhase.FINISHED
        maybeRestoreLoadout(player, state)
        return state.loadoutRestored
    }

    private fun handlerFor(mode: AirDropConfig.CarrierMode): CarrierModeHandler<PlayerT> {
        return handlers.getValue(mode)
    }

    private fun ensureLoadoutStored(player: PlayerT, state: dev.bingoparachute.session.AirDropPlayerState) {
        if (state.loadoutStored) {
            return
        }

        loadoutCustodian.captureAndClear(state.playerUuid, player)
        state.loadoutStored = true
        state.loadoutRestored = false
        if (configManager.config.debugLogging) {
            log.info("Stored and cleared loadout for player {}", state.playerUuid)
        }
    }

    private fun maybeRestoreLoadout(player: PlayerT, state: dev.bingoparachute.session.AirDropPlayerState) {
        if (!state.loadoutStored || state.loadoutRestored) {
            return
        }

        if (state.phase != dev.bingoparachute.session.AirDropPhase.FINISHED) {
            return
        }

        if (loadoutCustodian.restore(state.playerUuid, player)) {
            state.loadoutRestored = true
            if (configManager.config.debugLogging) {
                log.info(
                    "Restored loadout for player {} (reason={})",
                    state.playerUuid,
                    state.finishedReason ?: "unknown"
                )
            }
        }
    }
}

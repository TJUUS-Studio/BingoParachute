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
    private val notificationAdapter: PlayerNotificationAdapter<PlayerT>,
    private val finishedHookAdapter: AirDropFinishedHookAdapter<PlayerT>,
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
        val completedPlayers = mutableListOf<UUID>()
        for ((playerUuid, state) in session.playerStates) {
            val player = resolvePlayer(playerUuid) ?: continue
            if (state.phase == dev.bingoparachute.session.AirDropPhase.FINISHED) {
                maybeRestoreLoadout(player, state)
                maybeDispatchFinishedHook(player, state)
                if (state.loadoutRestored && state.finishHookDispatched) {
                    completedPlayers += playerUuid
                }
                continue
            }
            if (tick < state.activationTick) {
                maybeSendPvpPendingNotice(player, state, session.isPvpEnabled, config)
                continue
            }
            ensureLoadoutStored(player, state)
            if (handleTimeout(player, state, tick, config)) {
                maybeSendPvpProtectionEnded(player, state, session.isPvpEnabled, tick)
                maybeRestoreLoadout(player, state)
                continue
            }
            handlerFor(state.mode).tick(
                player = player,
                session = session,
                state = state,
                tick = tick,
                config = config,
            )
            maybeSendPvpProtectionEnded(player, state, session.isPvpEnabled, tick)
            maybeRestoreLoadout(player, state)
            maybeDispatchFinishedHook(player, state)
            if (state.phase == dev.bingoparachute.session.AirDropPhase.FINISHED && state.loadoutRestored && state.finishHookDispatched) {
                completedPlayers += playerUuid
            }
        }
        if (completedPlayers.isNotEmpty()) {
            completedPlayers.forEach(session.playerStates::remove)
            if (config.debugLogging) {
                log.info("Removed {} completed players from active airdrop tracking", completedPlayers.size)
            }
        }
    }

    fun cleanup(
        resolvePlayer: (UUID) -> PlayerT?,
        reason: String,
    ) {
        val session = sessionManager.currentSession ?: return
        for ((playerUuid, state) in session.playerStates) {
            val player = resolvePlayer(playerUuid) ?: continue
            finalizeState(state, reason)
            handlerFor(state.mode).cleanup(player, state)
            maybeRestoreLoadout(player, state)
            maybeDispatchFinishedHook(player, state)
        }
        loadoutCustodian.clearSnapshots()
    }

    fun cleanupPlayer(
        playerUuid: UUID,
        player: PlayerT,
        reason: String,
    ) {
        cleanupPlayer(playerUuid, player, reason, restoreLoadout = true)
    }

    fun cleanupPlayer(
        playerUuid: UUID,
        player: PlayerT,
        reason: String,
        restoreLoadout: Boolean,
    ) {
        val state = sessionManager.currentSession?.playerStates?.get(playerUuid) ?: return
        finalizeState(state, reason)
        handlerFor(state.mode).cleanup(player, state)
        if (restoreLoadout) {
            maybeRestoreLoadout(player, state)
        }
        maybeDispatchFinishedHook(player, state)
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
        finalizeState(state, "respawn_restore")
        maybeRestoreLoadout(player, state)
        maybeDispatchFinishedHook(player, state)
        return state.loadoutRestored
    }

    private fun handlerFor(mode: AirDropConfig.CarrierMode): CarrierModeHandler<PlayerT> {
        return handlers.getValue(mode)
    }

    private fun handleTimeout(
        player: PlayerT,
        state: dev.bingoparachute.session.AirDropPlayerState,
        tick: Long,
        config: AirDropConfig,
    ): Boolean {
        if (state.phase == dev.bingoparachute.session.AirDropPhase.FINISHED || state.spawnedAtTick == 0L) {
            return false
        }

        val timeoutTicks = config.pvpProtectionSeconds * 20L
        val deadlineTick = state.spawnedAtTick + timeoutTicks
        val remainingTicks = deadlineTick - tick
        maybeSendTimeoutCountdown(player, state, remainingTicks)

        if (remainingTicks > 0L) {
            return false
        }

        finalizeState(state, "timeout")
        state.timeoutFallImmuneUntilTick = tick + config.timeoutFallImmunitySeconds * 20L
        sessionManager.grantTimeoutFallImmunity(state.playerUuid, state.timeoutFallImmuneUntilTick)
        handlerFor(state.mode).cleanup(player, state)
        if (config.debugLogging) {
            log.info(
                "Forced airdrop finish on timeout for player {} (extraFallImmunitySeconds={})",
                state.playerUuid,
                config.timeoutFallImmunitySeconds
            )
        }
        return true
    }

    private fun maybeSendTimeoutCountdown(
        player: PlayerT,
        state: dev.bingoparachute.session.AirDropPlayerState,
        remainingTicks: Long,
    ) {
        if (remainingTicks <= 0L) {
            return
        }

        val remainingSeconds = ((remainingTicks + 19L) / 20L).toInt()
        if (remainingSeconds !in 1..10) {
            return
        }
        if (state.lastTimeoutWarningSecond == remainingSeconds) {
            return
        }

        state.lastTimeoutWarningSecond = remainingSeconds
        notificationAdapter.sendTimeoutCountdown(player, remainingSeconds)
    }

    private fun maybeSendPvpPendingNotice(
        player: PlayerT,
        state: dev.bingoparachute.session.AirDropPlayerState,
        isPvpEnabled: Boolean,
        config: AirDropConfig,
    ) {
        if (!isPvpEnabled || state.pvpStartNoticeSent) {
            return
        }

        state.pvpStartNoticeSent = true
        notificationAdapter.sendPvpProtectionPending(player, config.pvpProtectionSeconds)
    }

    private fun maybeSendPvpProtectionEnded(
        player: PlayerT,
        state: dev.bingoparachute.session.AirDropPlayerState,
        isPvpEnabled: Boolean,
        tick: Long,
    ) {
        if (!isPvpEnabled || state.pvpEndNoticeSent) {
            return
        }
        if (state.spawnedAtTick == 0L || tick < state.pvpProtectedUntilTick) {
            return
        }
        if (state.finishedReason == "timeout") {
            return
        }

        state.pvpEndNoticeSent = true
        notificationAdapter.sendPvpProtectionEnded(player)
    }

    private fun finalizeState(
        state: dev.bingoparachute.session.AirDropPlayerState,
        reason: String,
    ) {
        state.phase = dev.bingoparachute.session.AirDropPhase.FINISHED
        state.finishedReason = reason
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

    private fun maybeDispatchFinishedHook(player: PlayerT, state: dev.bingoparachute.session.AirDropPlayerState) {
        if (state.phase != dev.bingoparachute.session.AirDropPhase.FINISHED) {
            return
        }
        if (state.finishHookDispatched) {
            return
        }
        if (!state.loadoutRestored) {
            return
        }

        finishedHookAdapter.onFinished(player, state)
        state.finishHookDispatched = true
    }
}

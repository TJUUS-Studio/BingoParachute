package dev.bingoparachute.session

import dev.bingoparachute.config.AirDropConfig
import dev.bingoparachute.model.Position3d
import org.slf4j.Logger
import java.util.UUID

class AirDropSessionManager(
    private val log: Logger,
) {
    @Volatile
    var currentTick: Long = 0L
        private set

    @Volatile
    var isBingoDetected: Boolean = false
        private set

    @Volatile
    var currentSession: AirDropSession? = null
        private set

    fun setBingoDetected(value: Boolean) {
        isBingoDetected = value
        log.info("Bingo detection status updated: {}", value)
    }

    fun onServerStarted() {
        log.info("Session manager ready on server start")
    }

    fun onServerStopping() {
        val previous = currentSession
        currentSession = null
        log.info(
            "Server stopping; cleared airdrop session (hadSession={})",
            previous != null
        )
    }

    fun onServerTick(tick: Long) {
        currentTick = tick
        val session = currentSession ?: return
        if (session.startedAtTick == 0L) {
            session.startedAtTick = tick
        }
    }

    fun onPlayerJoin(playerUuid: UUID) {
        val tracked = currentSession?.playerStates?.containsKey(playerUuid) == true
        log.info("Player joined: {} (trackedInSession={})", playerUuid, tracked)
    }

    fun onPlayerDisconnect(playerUuid: UUID) {
        val tracked = currentSession?.playerStates?.containsKey(playerUuid) == true
        log.info("Player disconnected: {} (trackedInSession={})", playerUuid, tracked)
    }

    fun onPlayerRespawn(playerUuid: UUID) {
        val tracked = currentSession?.playerStates?.containsKey(playerUuid) == true
        log.info("Player respawned: {} (trackedInSession={})", playerUuid, tracked)
    }

    fun onBingoInit() {
        log.info("Detected Bingo initialization")
    }

    fun onGameStarted(
        sessionId: UUID,
        players: Collection<UUID>,
        mode: AirDropConfig.CarrierMode,
        playerOrigins: Map<UUID, Position3d> = emptyMap(),
        playerOriginSources: Map<UUID, String> = emptyMap(),
        activationTick: Long = currentTick,
        isPvpEnabled: Boolean = false,
    ) {
        val session = AirDropSession(sessionId = sessionId, isPvpEnabled = isPvpEnabled)
        session.playerStates.putAll(
            players.associateWith { uuid ->
                AirDropPlayerState(
                    playerUuid = uuid,
                    sessionId = sessionId,
                    mode = mode,
                    origin = playerOrigins[uuid] ?: Position3d.ZERO,
                    originSource = playerOriginSources[uuid] ?: "player_position_fallback",
                    activationTick = activationTick,
                )
            }
        )
        currentSession = session

        log.info(
            "Created airdrop session {} with {} tracked players (playersWithPreferredOrigin={}, activationTick={}, pvpEnabled={})",
            sessionId,
            session.playerStates.size,
            playerOrigins.size,
            activationTick,
            isPvpEnabled,
        )
    }

    fun onGameReset() {
        val previous = currentSession
        currentSession = null
        log.info(
            "Cleared airdrop session on GAME_RESET (hadSession={})",
            previous != null
        )
    }

    fun onGameEnded(sessionId: UUID?) {
        val previous = currentSession
        currentSession = null
        log.info(
            "Cleared airdrop session on GAME_ENDED (eventSessionId={}, hadSession={})",
            sessionId,
            previous != null
        )
    }

    fun isAirdropActive(playerUuid: UUID): Boolean {
        val state = currentSession?.playerStates?.get(playerUuid) ?: return false
        return state.spawnedAtTick != 0L && state.phase != AirDropPhase.FINISHED
    }

    fun isFallDamageImmune(playerUuid: UUID): Boolean {
        val state = currentSession?.playerStates?.get(playerUuid) ?: return false
        if (state.spawnedAtTick == 0L) {
            return false
        }
        if (state.phase != AirDropPhase.FINISHED) {
            return true
        }
        return currentTick < state.timeoutFallImmuneUntilTick
    }
}

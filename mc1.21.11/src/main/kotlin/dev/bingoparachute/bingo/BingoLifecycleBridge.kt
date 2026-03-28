package dev.bingoparachute.bingo

import dev.bingoparachute.BingoParachuteMod
import dev.bingoparachute.airdrop.AirDropRuntimeController
import dev.bingoparachute.model.BingoSnapshot
import dev.bingoparachute.model.Position3d
import dev.bingoparachute.session.AirDropSessionManager
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import java.util.UUID
import java.util.function.Consumer

class BingoLifecycleBridge(
    private val sessionManager: AirDropSessionManager,
    private val runtimeController: AirDropRuntimeController,
) {
    private val apiFacade = BingoApiFacade()
    private var initialized = false
    private var countdownAnchorGameId: UUID? = null
    private var countdownAnchors: Map<UUID, Position3d> = emptyMap()
    private var countdownAnchorSources: Map<UUID, String> = emptyMap()

    fun initialize() {
        if (initialized) return
        initialized = true

        registerListener("INIT") { _ ->
            sessionManager.onBingoInit()
            Unit
        }
        registerListener("GAME_STARTING") { _ ->
            clearCountdownAnchors()
            Unit
        }
        registerListener("GAME_STARTED") { args ->
            val snapshot = apiFacade.readSnapshotOrNull()
            val sessionId = snapshot?.gameId
                ?: args.firstOrNull()?.let(::extractGameId)
                ?: UUID.randomUUID()
            val preparedOrigins = if (countdownAnchorGameId == sessionId && countdownAnchors.isNotEmpty()) {
                countdownAnchors
            } else {
                snapshot?.playerOrigins ?: emptyMap()
            }
            val preparedOriginSources = if (countdownAnchorGameId == sessionId && countdownAnchorSources.isNotEmpty()) {
                countdownAnchorSources
            } else {
                snapshot?.playerOriginSources ?: emptyMap()
            }
            val startDelayTicks = if (countdownAnchorGameId == sessionId && countdownAnchors.isNotEmpty()) {
                0
            } else {
                BingoParachuteMod.configManager.config.startDelayTicks
            }
            val activationTick = sessionManager.currentTick + startDelayTicks
            sessionManager.onGameStarted(
                sessionId = sessionId,
                players = snapshot?.activePlayerIds ?: emptyList(),
                mode = BingoParachuteMod.configManager.config.mode,
                playerOrigins = preparedOrigins,
                playerOriginSources = preparedOriginSources,
                activationTick = activationTick,
                isPvpEnabled = snapshot?.isPvpEnabled == true,
            )
            BingoParachuteMod.log.info(
                "GAME_STARTED snapshot resolved: status={}, activePlayers={}, teamsWithSpawnpoint={}, activationTick={}, startDelayTicks={}, pvpEnabled={}",
                snapshot?.status,
                snapshot?.activePlayerIds?.size ?: 0,
                snapshot?.teams?.count { it.spawnpoint != null } ?: 0,
                activationTick,
                startDelayTicks,
                snapshot?.isPvpEnabled,
            )
            if (BingoParachuteMod.configManager.config.debugLogging) {
                BingoParachuteMod.log.info(
                    "GAME_STARTED origins prepared for {} players; sample={}",
                    preparedOrigins.size,
                    snapshot?.activePlayerIds?.take(3)?.joinToString { playerId ->
                        val origin = preparedOrigins[playerId]?.toString() ?: "player_position"
                        val source = preparedOriginSources[playerId] ?: "player_position_fallback"
                        "$playerId=$origin@$source"
                    } ?: "none"
                )
            }
            clearCountdownAnchors()
            Unit
        }
        registerListener("GAME_RESET") {
            runtimeController.onGameReset(BingoParachuteMod.server)
            sessionManager.onGameReset()
            clearCountdownAnchors()
            Unit
        }
        registerListener("GAME_ENDED") { args ->
            runtimeController.onGameEnded(BingoParachuteMod.server)
            sessionManager.onGameEnded(args.firstOrNull()?.let(::extractGameId))
            clearCountdownAnchors()
            Unit
        }
    }

    fun onServerTick(server: MinecraftServer) {
        if (!BingoParachuteMod.configManager.config.enabled) {
            return
        }
        if (sessionManager.currentSession != null) {
            return
        }
        if (apiFacade.readRawStateOrNull() != "COUNTDOWN") {
            return
        }

        val snapshot = apiFacade.readSnapshotOrNull() ?: return
        if (snapshot.playerOrigins.isEmpty()) {
            return
        }

        val anchors = prepareCountdownAnchors(snapshot)
        countdownAnchorGameId = snapshot.gameId
        countdownAnchors = anchors
        countdownAnchorSources = anchors.keys.associateWith { "countdown_anchor" }

        for ((playerUuid, anchor) in anchors) {
            val player = server.playerManager.getPlayer(playerUuid) ?: continue
            pinPlayerToCountdownAnchor(player, anchor)
        }
    }

    private fun registerListener(fieldName: String, handler: (Array<out Any?>) -> Unit) {
        try {
            val eventsClass = Class.forName("me.jfenn.bingo.api.BingoEvents")
            val eventField = eventsClass.getField(fieldName)
            val eventListener = eventField.get(null)
            val registerMethod = eventListener.javaClass.methods.firstOrNull {
                it.name == "register" &&
                    it.parameterCount == 1 &&
                    Consumer::class.java.isAssignableFrom(it.parameterTypes[0])
            }

            if (registerMethod == null) {
                BingoParachuteMod.log.warn("Bingo event {} exists, but listener registration method was not found", fieldName)
                return
            }

            val callback = Consumer<Any?> { event ->
                handler(if (event == null) emptyArray() else arrayOf(event))
            }
            registerMethod.invoke(eventListener, callback)
            BingoParachuteMod.log.info("Registered Bingo listener for {}", fieldName)
        } catch (e: ClassNotFoundException) {
            BingoParachuteMod.log.info("Bingo API not found; {} listener not registered yet", fieldName)
        } catch (e: Throwable) {
            BingoParachuteMod.log.error("Failed to register Bingo listener for $fieldName", e)
        }
    }

    private fun extractGameId(event: Any): UUID? {
        return runCatching {
            event.javaClass.methods
                .firstOrNull { it.name == "getId" && it.parameterCount == 0 }
                ?.invoke(event) as? UUID
        }.getOrNull()
    }

    private fun prepareCountdownAnchors(snapshot: BingoSnapshot): Map<UUID, Position3d> {
        return buildMap {
            for ((playerUuid, origin) in snapshot.playerOrigins) {
                put(playerUuid, createCountdownAnchor(playerUuid, origin))
            }
        }
    }

    private fun createCountdownAnchor(playerUuid: UUID, origin: Position3d): Position3d {
        val seed = playerUuid.mostSignificantBits xor playerUuid.leastSignificantBits
        val offsetX = normalizedOffset(seed)
        val offsetZ = normalizedOffset(seed.rotateRight(17))
        return Position3d(
            x = origin.x + 0.5 + offsetX,
            y = BingoParachuteMod.configManager.config.spawnHeight.toDouble(),
            z = origin.z + 0.5 + offsetZ,
        )
    }

    private fun pinPlayerToCountdownAnchor(player: ServerPlayerEntity, anchor: Position3d) {
        val world = player.entityWorld as? ServerWorld ?: return
        val targetY = minOf(anchor.y, (world.topYInclusive - 4).toDouble())
        val vehicle = player.vehicle
        if (vehicle != null) {
            vehicle.refreshPositionAndAngles(anchor.x, targetY - 0.4, anchor.z, vehicle.yaw, vehicle.pitch)
            vehicle.velocity = vehicle.velocity.multiply(0.0)
        }
        player.networkHandler.requestTeleport(anchor.x, targetY, anchor.z, player.yaw, player.pitch)
        player.velocity = player.velocity.multiply(0.0)
        player.fallDistance = 0.0
    }

    private fun normalizedOffset(seed: Long): Double {
        val bucket = (seed ushr 16).toInt() and 0xffff
        return (bucket / 65535.0 - 0.5) * 3.0
    }

    private fun clearCountdownAnchors() {
        countdownAnchorGameId = null
        countdownAnchors = emptyMap()
        countdownAnchorSources = emptyMap()
    }
}

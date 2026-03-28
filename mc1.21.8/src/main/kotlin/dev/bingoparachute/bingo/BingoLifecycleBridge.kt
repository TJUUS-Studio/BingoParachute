package dev.bingoparachute.bingo

import dev.bingoparachute.BingoParachuteMod
import dev.bingoparachute.airdrop.AirDropRuntimeController
import dev.bingoparachute.session.AirDropSessionManager
import java.util.UUID
import java.util.function.Consumer

class BingoLifecycleBridge(
    private val sessionManager: AirDropSessionManager,
    private val runtimeController: AirDropRuntimeController,
) {
    private val apiFacade = BingoApiFacade()
    private var initialized = false

    fun initialize() {
        if (initialized) return
        initialized = true

        registerListener("INIT") { _ ->
            sessionManager.onBingoInit()
            Unit
        }
        registerListener("GAME_STARTED") { args ->
            val snapshot = apiFacade.readSnapshotOrNull()
            val sessionId = snapshot?.gameId
                ?: args.firstOrNull()?.let(::extractGameId)
                ?: UUID.randomUUID()
            val startDelayTicks = BingoParachuteMod.configManager.config.startDelayTicks
            val activationTick = sessionManager.currentTick + startDelayTicks
            sessionManager.onGameStarted(
                sessionId = sessionId,
                players = snapshot?.activePlayerIds ?: emptyList(),
                mode = BingoParachuteMod.configManager.config.mode,
                playerOrigins = snapshot?.playerOrigins ?: emptyMap(),
                playerOriginSources = snapshot?.playerOriginSources ?: emptyMap(),
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
                    snapshot?.playerOrigins?.size ?: 0,
                    snapshot?.activePlayerIds?.take(3)?.joinToString { playerId ->
                        val origin = snapshot.playerOrigins[playerId]?.toString() ?: "player_position"
                        val source = snapshot.playerOriginSources[playerId] ?: "player_position_fallback"
                        "$playerId=$origin@$source"
                    } ?: "none"
                )
            }
            Unit
        }
        registerListener("GAME_RESET") {
            runtimeController.onGameReset(BingoParachuteMod.server)
            sessionManager.onGameReset()
            Unit
        }
        registerListener("GAME_ENDED") { args ->
            runtimeController.onGameEnded(BingoParachuteMod.server)
            sessionManager.onGameEnded(args.firstOrNull()?.let(::extractGameId))
            Unit
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
}

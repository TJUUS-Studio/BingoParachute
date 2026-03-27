package dev.bingoparachute.bingo

import dev.bingoparachute.BingoParachuteMod
import dev.bingoparachute.airdrop.AirDropRuntimeController
import dev.bingoparachute.session.AirDropSessionManager
import java.lang.reflect.Proxy
import java.util.UUID

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
            sessionManager.onGameStarted(
                sessionId = sessionId,
                players = snapshot?.activePlayerIds ?: emptyList(),
                mode = BingoParachuteMod.configManager.config.mode,
                playerOrigins = snapshot?.playerOrigins ?: emptyMap(),
            )
            BingoParachuteMod.log.info(
                "GAME_STARTED snapshot resolved: status={}, activePlayers={}, teamsWithSpawnpoint={}",
                snapshot?.status,
                snapshot?.activePlayerIds?.size ?: 0,
                snapshot?.teams?.count { it.spawnpoint != null } ?: 0,
            )
            if (BingoParachuteMod.configManager.config.debugLogging) {
                BingoParachuteMod.log.info(
                    "GAME_STARTED origins prepared for {} players; sample={}",
                    snapshot?.playerOrigins?.size ?: 0,
                    snapshot?.playerOrigins?.entries?.take(3)?.joinToString { "${it.key}=${it.value}" } ?: "none"
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
            val eventListenerClass = eventListener.javaClass
            val callbackMethod = eventListenerClass.methods.firstOrNull {
                it.name == "invoke" && it.parameterCount == 1 && it.parameterTypes[0].name.contains("Function")
            }

            if (callbackMethod == null) {
                BingoParachuteMod.log.warn("Bingo event {} exists, but listener registration method was not found", fieldName)
                return
            }

            val functionClass = callbackMethod.parameterTypes[0]
            val proxy = Proxy.newProxyInstance(
                functionClass.classLoader,
                arrayOf(functionClass)
            ) { _, method, args ->
                when (method.name) {
                    "invoke" -> {
                        handler(args ?: emptyArray())
                        Unit
                    }
                    "toString" -> "BingoParachute-$fieldName-listener"
                    "hashCode" -> System.identityHashCode(this)
                    "equals" -> false
                    else -> null
                }
            }

            callbackMethod.invoke(eventListener, proxy)
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

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
            maybeOverrideBingoCountdown("INIT")
            sessionManager.onBingoInit()
            Unit
        }
        registerListener("GAME_STARTING") { _ ->
            maybeOverrideBingoCountdown("GAME_STARTING")
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

    private fun maybeOverrideBingoCountdown(trigger: String) {
        if (!BingoParachuteMod.configManager.config.skipBingoCountdown) {
            return
        }

        val server = BingoParachuteMod.server ?: return
        runCatching {
            val bingoKoinClass = Class.forName("me.jfenn.bingo.platform.scope.BingoKoin")
            val scope = bingoKoinClass.methods
                .firstOrNull { it.name == "getScope" && it.parameterCount == 1 }
                ?.invoke(null, server)
                ?: return@runCatching false

            val configClass = Class.forName("me.jfenn.bingo.common.config.BingoConfig")
            val config = resolveFromScope(scope, configClass) ?: return@runCatching false
            val delayChanged = setIntField(config, "countdownDelayTicks", 0)
            val secondsChanged = setIntField(config, "countdownSeconds", 0)
            delayChanged || secondsChanged
        }.onSuccess { changed ->
            if (changed == true) {
                BingoParachuteMod.log.info("Forced Bingo countdown config to zero at {}", trigger)
            }
        }.onFailure { throwable ->
            BingoParachuteMod.log.warn("Failed to override Bingo countdown config at {}", trigger, throwable)
        }
    }

    private fun resolveFromScope(scope: Any, targetClass: Class<*>): Any? {
        scope.javaClass.methods.firstOrNull { method ->
            method.name == "get" &&
                method.parameterCount >= 1 &&
                method.parameterTypes[0] == Class::class.java
        }?.let { method ->
            val args = arrayOfNulls<Any>(method.parameterCount)
            args[0] = targetClass
            return method.invoke(scope, *args)
        }

        val kClass = runCatching {
            Class.forName("kotlin.jvm.JvmClassMappingKt").methods
                .firstOrNull { it.name == "getKotlinClass" && it.parameterCount == 1 }
                ?.invoke(null, targetClass)
        }.getOrNull() ?: return null

        scope.javaClass.methods.firstOrNull { method ->
            method.name == "get" &&
                method.parameterCount >= 1 &&
                method.parameterTypes[0].name == "kotlin.reflect.KClass"
        }?.let { method ->
            val args = arrayOfNulls<Any>(method.parameterCount)
            args[0] = kClass
            return method.invoke(scope, *args)
        }

        return null
    }

    private fun setIntField(target: Any, fieldName: String, value: Int): Boolean {
        val field = runCatching {
            target.javaClass.getDeclaredField(fieldName).apply { isAccessible = true }
        }.getOrNull() ?: return false
        val previous = runCatching { field.getInt(target) }.getOrNull() ?: return false
        if (previous == value) {
            return false
        }
        field.setInt(target, value)
        return true
    }
}

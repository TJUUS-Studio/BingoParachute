package dev.bingoparachute.airdrop

import dev.bingoparachute.BingoParachuteMod
import dev.bingoparachute.session.AirDropPlayerState
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.command.ServerCommandSource
import java.lang.reflect.Method

class ServerPlayerAirdropFinishedHookAdapter : AirDropFinishedHookAdapter<ServerPlayerEntity> {
    override fun onFinished(player: ServerPlayerEntity, state: AirDropPlayerState) {
        runCatching {
            val server = BingoParachuteMod.server ?: return
            val source = player.commandSource.toSilentSource()
            val commandManager = server.commandManager
            val reason = state.finishedReason ?: "unknown"
            val reasonCode = AirDropFinishReason.codeOf(reason)
            val modeCode = AirDropFinishReason.modeCodeOf(state.mode)

            commandManager.executeCommand(source, "scoreboard objectives add bp_reason dummy")
            commandManager.executeCommand(source, "scoreboard objectives add bp_mode dummy")
            commandManager.executeCommand(source, "scoreboard players set @s bp_reason $reasonCode")
            commandManager.executeCommand(source, "scoreboard players set @s bp_mode $modeCode")
            commandManager.executeCommand(source, "function bingo_parachute:airdrop/finished")
            commandManager.executeCommand(source, "function bingo_parachute:airdrop/finished/$reason")

            if (BingoParachuteMod.configManager.config.debugLogging) {
                BingoParachuteMod.log.info(
                    "Dispatched airdrop finished datapack hooks for player {} (reason={}, mode={})",
                    player.uuid,
                    reason,
                    state.mode
                )
            }
        }.onFailure { throwable ->
            BingoParachuteMod.log.error(
                "Failed to dispatch airdrop finished datapack hooks for player {}",
                player.uuid,
                throwable
            )
        }
    }

    private fun ServerCommandSource.toSilentSource(): ServerCommandSource {
        return runCatching {
            this.javaClass.methods.firstOrNull { it.name == "withSilent" && it.parameterCount == 0 }
                ?.invoke(this) as? ServerCommandSource
        }.getOrNull() ?: this
    }

    private fun Any.executeCommand(source: ServerCommandSource, command: String) {
        val methods = javaClass.methods.toList()
        if (invokeCompatible(methods, "executeWithPrefix", source, command)) {
            return
        }
        if (invokeCompatible(methods, "execute", source, command)) {
            return
        }
        if (invokeDispatcher(command, source)) {
            return
        }
        BingoParachuteMod.log.warn(
            "Skipping datapack hook command because no compatible execution method was found on {}: {}",
            javaClass.name,
            command
        )
    }

    private fun Any.invokeCompatible(
        methods: List<Method>,
        methodName: String,
        source: ServerCommandSource,
        command: String,
    ): Boolean {
        val method = methods.firstOrNull { candidate ->
            candidate.name == methodName &&
                candidate.parameterCount == 2 &&
                candidate.parameterTypes[0].isAssignableFrom(source.javaClass) &&
                candidate.parameterTypes[1] == String::class.java
        } ?: return false

        method.invoke(this, source, command)
        return true
    }

    private fun Any.invokeDispatcher(command: String, source: ServerCommandSource): Boolean {
        val dispatcher = runCatching {
            javaClass.methods.firstOrNull { it.name == "getDispatcher" && it.parameterCount == 0 }
                ?.invoke(this)
        }.getOrNull() ?: runCatching {
            javaClass.fields.firstOrNull { it.name == "dispatcher" }?.get(this)
        }.getOrNull() ?: return false

        val executeMethod = dispatcher.javaClass.methods.firstOrNull { method ->
            method.name == "execute" &&
                method.parameterCount == 2 &&
                method.parameterTypes[0] == String::class.java &&
                method.parameterTypes[1].isAssignableFrom(source.javaClass)
        } ?: return false

        executeMethod.invoke(dispatcher, command, source)
        return true
    }
}

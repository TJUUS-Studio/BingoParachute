package dev.bingoparachute.airdrop

import dev.bingoparachute.BingoParachuteMod
import dev.bingoparachute.session.AirDropPlayerState
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.command.ServerCommandSource

class ServerPlayerAirdropFinishedHookAdapter : AirDropFinishedHookAdapter<ServerPlayerEntity> {
    override fun onFinished(player: ServerPlayerEntity, state: AirDropPlayerState) {
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
    }

    private fun ServerCommandSource.toSilentSource(): ServerCommandSource {
        return runCatching {
            this.javaClass.methods.firstOrNull { it.name == "withSilent" && it.parameterCount == 0 }
                ?.invoke(this) as? ServerCommandSource
        }.getOrNull() ?: this
    }

    private fun Any.executeCommand(source: ServerCommandSource, command: String) {
        val methods = javaClass.methods.toList()
        val withPrefix = methods.firstOrNull { method ->
            method.name == "executeWithPrefix" &&
                method.parameterCount == 2 &&
                method.parameterTypes[0].isAssignableFrom(source.javaClass) &&
                method.parameterTypes[1] == String::class.java
        }
        if (withPrefix != null) {
            withPrefix.invoke(this, source, command)
            return
        }

        val execute = methods.firstOrNull { method ->
            method.name == "execute" &&
                method.parameterCount == 2 &&
                method.parameterTypes[0].isAssignableFrom(source.javaClass) &&
                method.parameterTypes[1] == String::class.java
        }
        if (execute != null) {
            execute.invoke(this, source, command)
            return
        }

        error("No compatible command execution method found on ${javaClass.name}")
    }
}

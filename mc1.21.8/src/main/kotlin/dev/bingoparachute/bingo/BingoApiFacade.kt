package dev.bingoparachute.bingo

import dev.bingoparachute.model.BingoSnapshot
import dev.bingoparachute.model.BingoTeamSnapshot
import java.util.UUID

class BingoApiFacade {
    fun readSnapshotOrNull(): BingoSnapshot? {
        val bingoApiClass = runCatching {
            Class.forName("me.jfenn.bingo.api.BingoApi")
        }.getOrNull() ?: return null

        val game = runCatching {
            bingoApiClass.methods.firstOrNull { it.name == "getGame" && it.parameterCount == 0 }
                ?.invoke(null)
        }.getOrNull() ?: return null

        val teamsObject = runCatching {
            bingoApiClass.methods.firstOrNull { it.name == "getTeams" && it.parameterCount == 0 }
                ?.invoke(null)
        }.getOrNull() ?: return null

        val gameId = readUuid(game, "getId") ?: return null
        val status = runCatching {
            game.javaClass.methods.firstOrNull { it.name == "getStatus" && it.parameterCount == 0 }
                ?.invoke(game)
                ?.toString()
        }.getOrNull() ?: "UNKNOWN"

        val teams = (teamsObject as? Iterable<*>)?.mapNotNull { team ->
            val teamObject = team ?: return@mapNotNull null
            val id = readString(teamObject, "getId") ?: return@mapNotNull null
            val players = readUuidList(teamObject, "getPlayers")
            val spawnpoint = readPosition(teamObject, "getSpawnpoint", "spawnpoint")
            BingoTeamSnapshot(
                id = id,
                players = players,
                spawnpoint = spawnpoint,
            )
        } ?: emptyList()

        return BingoSnapshot(
            gameId = gameId,
            status = status,
            teams = teams
        )
    }

    private fun readUuid(target: Any, getterName: String): UUID? {
        return runCatching {
            target.javaClass.methods.firstOrNull { it.name == getterName && it.parameterCount == 0 }
                ?.invoke(target) as? UUID
        }.getOrNull()
    }

    private fun readString(target: Any, getterName: String): String? {
        return runCatching {
            target.javaClass.methods.firstOrNull { it.name == getterName && it.parameterCount == 0 }
                ?.invoke(target)
                ?.toString()
        }.getOrNull()
    }

    private fun readUuidList(target: Any, getterName: String): List<UUID> {
        return runCatching {
            val value = target.javaClass.methods.firstOrNull { it.name == getterName && it.parameterCount == 0 }
                ?.invoke(target)
            (value as? Iterable<*>)?.mapNotNull { it as? UUID } ?: emptyList()
        }.getOrDefault(emptyList())
    }

    private fun readPosition(target: Any, getterName: String, fieldName: String): dev.bingoparachute.model.Position3d? {
        val value = runCatching {
            target.javaClass.methods.firstOrNull { it.name == getterName && it.parameterCount == 0 }
                ?.invoke(target)
        }.getOrNull() ?: runCatching {
            target.javaClass.getDeclaredField(fieldName).apply { isAccessible = true }.get(target)
        }.getOrNull() ?: return null

        val x = readCoordinate(value, "getX", "x") ?: return null
        val y = readCoordinate(value, "getY", "y") ?: return null
        val z = readCoordinate(value, "getZ", "z") ?: return null
        return dev.bingoparachute.model.Position3d(x, y, z)
    }

    private fun readCoordinate(target: Any, getterName: String, fieldName: String): Double? {
        return runCatching {
            (target.javaClass.methods.firstOrNull { it.name == getterName && it.parameterCount == 0 }
                ?.invoke(target) as? Number)?.toDouble()
        }.getOrNull() ?: runCatching {
            (target.javaClass.getDeclaredField(fieldName).apply { isAccessible = true }.get(target) as? Number)?.toDouble()
        }.getOrNull()
    }
}

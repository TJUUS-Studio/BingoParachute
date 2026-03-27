package dev.bingoparachute.model

import java.util.UUID

data class BingoSnapshot(
    val gameId: UUID,
    val status: String,
    val teams: List<BingoTeamSnapshot>,
) {
    val activePlayerIds: Set<UUID>
        get() = teams.flatMapTo(linkedSetOf()) { it.players }

    val playerOrigins: Map<UUID, Position3d>
        get() = buildMap {
            for (team in teams) {
                val origin = team.spawnpoint ?: continue
                for (player in team.players) {
                    put(player, origin)
                }
            }
        }

    val playerOriginSources: Map<UUID, String>
        get() = buildMap {
            for (team in teams) {
                val source = if (team.spawnpoint != null) {
                    "team_spawnpoint:${team.id}"
                } else {
                    "player_position_fallback"
                }
                for (player in team.players) {
                    put(player, source)
                }
            }
        }
}

data class BingoTeamSnapshot(
    val id: String,
    val players: List<UUID>,
    val spawnpoint: Position3d? = null,
)

package dev.bingoparachute.session

import java.util.UUID

data class AirDropSession(
    val sessionId: UUID,
    val playerStates: MutableMap<UUID, AirDropPlayerState> = linkedMapOf(),
    var startedAtTick: Long = 0L,
    val isPvpEnabled: Boolean = false,
)


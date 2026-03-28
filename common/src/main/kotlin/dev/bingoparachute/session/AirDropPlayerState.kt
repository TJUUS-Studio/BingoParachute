package dev.bingoparachute.session

import dev.bingoparachute.config.AirDropConfig
import dev.bingoparachute.model.Position3d
import java.util.UUID

data class AirDropPlayerState(
    val playerUuid: UUID,
    val sessionId: UUID,
    val mode: AirDropConfig.CarrierMode,
    var origin: Position3d,
    var originSource: String = "player_position_fallback",
    var phase: AirDropPhase = AirDropPhase.DESCENDING,
    var activationTick: Long = 0L,
    var spawnedAtTick: Long = 0L,
    var pvpProtectedUntilTick: Long = 0L,
    var loadoutStored: Boolean = false,
    var loadoutRestored: Boolean = false,
    var carrierEntityId: Int? = null,
    var finishedReason: String? = null,
    var lastTimeoutWarningSecond: Int? = null,
    var finishHookDispatched: Boolean = false,
)

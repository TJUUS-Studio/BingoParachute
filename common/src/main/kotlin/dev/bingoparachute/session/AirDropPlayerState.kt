package dev.bingoparachute.session

import dev.bingoparachute.config.AirDropConfig
import dev.bingoparachute.model.Position3d
import java.util.UUID

data class AirDropPlayerState(
    val playerUuid: UUID,
    val sessionId: UUID,
    val mode: AirDropConfig.CarrierMode,
    var origin: Position3d,
    var phase: AirDropPhase = AirDropPhase.DESCENDING,
    var spawnedAtTick: Long = 0L,
    var pvpProtectedUntilTick: Long = 0L,
    var loadoutStored: Boolean = false,
    var loadoutRestored: Boolean = false,
    var carrierEntityId: Int? = null,
    var finishedReason: String? = null,
)

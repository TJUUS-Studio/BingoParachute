package dev.bingoparachute.airdrop

import dev.bingoparachute.config.AirDropConfig
import dev.bingoparachute.session.AirDropPlayerState
import dev.bingoparachute.session.AirDropSession

interface CarrierModeHandler<PlayerT> {
    val mode: AirDropConfig.CarrierMode

    fun tick(
        player: PlayerT,
        session: AirDropSession,
        state: AirDropPlayerState,
        tick: Long,
        config: AirDropConfig,
    )

    fun cleanup(
        player: PlayerT,
        state: AirDropPlayerState,
    )
}

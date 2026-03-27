package dev.bingoparachute.airdrop

import dev.bingoparachute.config.AirDropConfig
import dev.bingoparachute.session.AirDropPlayerState
import dev.bingoparachute.session.AirDropSession
import org.slf4j.Logger

class FallbackCarrierHandler<PlayerT>(
    override val mode: AirDropConfig.CarrierMode,
    private val delegate: CarrierModeHandler<PlayerT>,
    private val log: Logger,
) : CarrierModeHandler<PlayerT> {
    override fun tick(
        player: PlayerT,
        session: AirDropSession,
        state: AirDropPlayerState,
        tick: Long,
        config: AirDropConfig,
    ) {
        log.debug("Carrier mode {} not implemented yet; delegating to {}", mode, delegate.mode)
        delegate.tick(player, session, state, tick, config)
    }

    override fun cleanup(player: PlayerT, state: AirDropPlayerState) {
        delegate.cleanup(player, state)
    }
}

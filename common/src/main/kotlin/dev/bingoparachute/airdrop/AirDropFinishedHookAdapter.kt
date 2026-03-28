package dev.bingoparachute.airdrop

import dev.bingoparachute.session.AirDropPlayerState

interface AirDropFinishedHookAdapter<PlayerT> {
    fun onFinished(player: PlayerT, state: AirDropPlayerState)
}

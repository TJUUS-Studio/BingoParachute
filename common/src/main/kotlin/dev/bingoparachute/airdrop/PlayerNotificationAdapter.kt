package dev.bingoparachute.airdrop

interface PlayerNotificationAdapter<PlayerT> {
    fun sendTimeoutCountdown(player: PlayerT, secondsRemaining: Int)
}

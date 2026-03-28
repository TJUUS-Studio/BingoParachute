package dev.bingoparachute.airdrop

interface PlayerNotificationAdapter<PlayerT> {
    fun sendPvpProtectionPending(player: PlayerT, protectionSeconds: Int)
    fun sendPvpProtectionEnded(player: PlayerT)
    fun sendTimeoutCountdown(player: PlayerT, secondsRemaining: Int)
}

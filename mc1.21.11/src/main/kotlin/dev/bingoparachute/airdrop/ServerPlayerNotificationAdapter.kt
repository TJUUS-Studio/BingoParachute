package dev.bingoparachute.airdrop

import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text

class ServerPlayerNotificationAdapter : PlayerNotificationAdapter<ServerPlayerEntity> {
    override fun sendTimeoutCountdown(player: ServerPlayerEntity, secondsRemaining: Int) {
        player.sendMessage(Text.literal("落地倒计时 ${secondsRemaining}s"), true)
    }
}

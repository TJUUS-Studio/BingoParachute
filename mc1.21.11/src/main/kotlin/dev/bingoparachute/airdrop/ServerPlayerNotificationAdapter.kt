package dev.bingoparachute.airdrop

import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text

class ServerPlayerNotificationAdapter : PlayerNotificationAdapter<ServerPlayerEntity> {
    override fun sendPvpProtectionPending(player: ServerPlayerEntity, protectionSeconds: Int) {
        player.sendMessage(Text.literal("本局允许 PvP。空降开始后将获得 ${protectionSeconds}s 保护。"), false)
    }

    override fun sendPvpProtectionEnded(player: ServerPlayerEntity) {
        player.sendMessage(Text.literal("PvP 保护已结束。"), false)
    }

    override fun sendTimeoutCountdown(player: ServerPlayerEntity, secondsRemaining: Int) {
        player.sendMessage(Text.literal("落地倒计时 ${secondsRemaining}s"), true)
    }
}

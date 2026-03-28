package dev.bingoparachute.airdrop

import dev.bingoparachute.config.AirDropConfig

object AirDropFinishReason {
    private val reasonCodes = mapOf(
        "touch_ground" to 1,
        "touch_water" to 2,
        "touch_lava" to 3,
        "timeout" to 4,
        "player_dead" to 5,
        "carrier_missing" to 6,
        "player_disconnect" to 7,
        "player_respawn" to 8,
        "game_reset" to 9,
        "game_ended" to 10,
        "server_stopping" to 11,
        "respawn_restore" to 12,
    )

    fun codeOf(reason: String?): Int {
        return reasonCodes[reason] ?: 0
    }

    fun modeCodeOf(mode: AirDropConfig.CarrierMode): Int {
        return when (mode) {
            AirDropConfig.CarrierMode.BAT -> 1
            AirDropConfig.CarrierMode.ELYTRA -> 2
        }
    }
}

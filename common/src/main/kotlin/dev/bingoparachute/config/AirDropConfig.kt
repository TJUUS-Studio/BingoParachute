package dev.bingoparachute.config

import kotlinx.serialization.Serializable

@Serializable
data class AirDropConfig(
    val enabled: Boolean = true,
    val debugLogging: Boolean = true,
    val mode: CarrierMode = CarrierMode.BAT,
    val startDelayTicks: Int = 10,
    val spawnHeight: Int = 196,
    val pvpProtectionSeconds: Int = 30,
    val timeoutFallImmunitySeconds: Int = 10,
    val bat: BatConfig = BatConfig(),
    val elytra: ElytraConfig = ElytraConfig(),
    val removeOnTouchGround: Boolean = true,
    val removeOnTouchWater: Boolean = true,
) {
    @Serializable
    enum class CarrierMode {
        BAT,
        ELYTRA,
    }

    @Serializable
    data class BatConfig(
        val descentSpeed: Double = 0.33,
        val horizontalSpeed: Double = 0.32,
        val maxHorizontalRadiusChunks: Double = 3.0,
    )

    @Serializable
    data class ElytraConfig(
        val glideSpeedScale: Double = 0.85,
        val maxDiveSpeed: Double = 0.9,
        val maxHorizontalRadiusChunks: Double = 3.5,
    )
}


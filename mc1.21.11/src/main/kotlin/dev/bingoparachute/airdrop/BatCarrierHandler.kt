package dev.bingoparachute.airdrop

import dev.bingoparachute.config.AirDropConfig
import dev.bingoparachute.model.Position3d
import dev.bingoparachute.session.AirDropPhase
import dev.bingoparachute.session.AirDropPlayerState
import dev.bingoparachute.session.AirDropSession
import net.minecraft.entity.EntityType
import net.minecraft.entity.passive.BatEntity
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.math.Vec3d
import org.slf4j.Logger
import kotlin.math.max
import kotlin.math.sqrt
import kotlin.random.Random

class BatCarrierHandler(
    private val log: Logger,
) : CarrierModeHandler<ServerPlayerEntity> {
    override val mode = AirDropConfig.CarrierMode.BAT

    override fun tick(
        player: ServerPlayerEntity,
        session: AirDropSession,
        state: AirDropPlayerState,
        tick: Long,
        config: AirDropConfig,
    ) {
        if (state.phase == AirDropPhase.FINISHED) {
            cleanup(player, state)
            return
        }

        if (state.spawnedAtTick == 0L) {
            startDescent(player, state, tick, config)
            return
        }

        val bat = player.vehicle as? BatEntity
        if (bat == null || bat.id != state.carrierEntityId) {
            finish(player, state, "carrier_missing")
            return
        }

        val finishReason = finishReason(player, config)
        if (finishReason != null) {
            finish(player, state, finishReason)
            return
        }

        applyBatVelocity(bat, state, config)
        player.fallDistance = 0.0
    }

    override fun cleanup(
        player: ServerPlayerEntity,
        state: AirDropPlayerState,
    ) {
        val vehicle = player.vehicle
        player.stopRiding()
        if (vehicle is BatEntity && vehicle.id == state.carrierEntityId) {
            vehicle.discard()
        }
        state.carrierEntityId = null
        player.fallDistance = 0.0
    }

    private fun startDescent(
        player: ServerPlayerEntity,
        state: AirDropPlayerState,
        tick: Long,
        config: AirDropConfig,
    ) {
        val world = player.entityWorld as ServerWorld
        val clampedHeight = max(player.y.toInt() + 8, world.topYInclusive - 4)
        val startY = minOf(config.spawnHeight, clampedHeight).toDouble()
        val preferredOrigin = state.origin.takeUnless { it == Position3d.ZERO }
        val baseX = preferredOrigin?.x?.plus(0.5) ?: player.x
        val baseZ = preferredOrigin?.z?.plus(0.5) ?: player.z
        val randomX = baseX + Random.nextDouble(-1.5, 1.5)
        val randomZ = baseZ + Random.nextDouble(-1.5, 1.5)

        state.origin = Position3d(randomX, startY, randomZ)
        state.spawnedAtTick = tick
        state.pvpProtectedUntilTick = tick + config.pvpProtectionSeconds * 20L

        player.networkHandler.requestTeleport(randomX, startY, randomZ, player.yaw, player.pitch)
        player.fallDistance = 0.0

        val bat = BatEntity(EntityType.BAT, world)
        bat.refreshPositionAndAngles(randomX, startY - 0.4, randomZ, player.yaw, 0f)
        bat.isInvulnerable = true
        bat.isSilent = true
        bat.setNoGravity(true)
        world.spawnEntity(bat)
        player.startRiding(bat, true, false)
        state.carrierEntityId = bat.id

        log.info(
            "Started bat descent for player {} in session {} at ({}, {}, {}) [originSource={}, activationTick={}, startedAtTick={}]",
            player.uuid,
            state.sessionId,
            randomX,
            startY,
            randomZ,
            state.originSource,
            state.activationTick,
            tick,
        )
    }

    private fun applyBatVelocity(
        bat: BatEntity,
        state: AirDropPlayerState,
        config: AirDropConfig,
    ) {
        val batConfig = config.bat
        val look = bat.firstPassenger?.rotationVector ?: Vec3d.ZERO
        val horizontalLook = Vec3d(look.x, 0.0, look.z)
        val horizontalLength = sqrt(horizontalLook.x * horizontalLook.x + horizontalLook.z * horizontalLook.z)
        val normalizedLook = if (horizontalLength > 0.0001) {
            horizontalLook.multiply(1.0 / horizontalLength)
        } else {
            Vec3d.ZERO
        }

        val fromOriginX = bat.x - state.origin.x
        val fromOriginZ = bat.z - state.origin.z
        val distanceFromOrigin = sqrt(fromOriginX * fromOriginX + fromOriginZ * fromOriginZ)

        val horizontalVelocity = if (distanceFromOrigin >= batConfig.maxHorizontalRadius) {
            val returnVector = Vec3d(state.origin.x - bat.x, 0.0, state.origin.z - bat.z)
            val returnLength = sqrt(returnVector.x * returnVector.x + returnVector.z * returnVector.z)
            if (returnLength > 0.0001) {
                returnVector.multiply(batConfig.horizontalSpeed / returnLength)
            } else {
                Vec3d.ZERO
            }
        } else {
            normalizedLook.multiply(batConfig.horizontalSpeed)
        }

        bat.velocity = Vec3d(horizontalVelocity.x, -batConfig.descentSpeed, horizontalVelocity.z)
    }

    private fun finishReason(
        player: ServerPlayerEntity,
        config: AirDropConfig,
    ): String? {
        if (config.removeOnTouchGround && player.isOnGround) {
            return "touch_ground"
        }
        if (config.removeOnTouchWater && player.isTouchingWater) {
            return "touch_water"
        }
        if (player.isInLava) {
            return "touch_lava"
        }
        if (!player.isAlive) {
            return "player_dead"
        }
        return null
    }

    private fun finish(
        player: ServerPlayerEntity,
        state: AirDropPlayerState,
        reason: String,
    ) {
        state.phase = AirDropPhase.FINISHED
        state.finishedReason = reason
        cleanup(player, state)
        log.info(
            "Finished bat descent for player {} in session {} (reason={})",
            player.uuid,
            state.sessionId,
            reason
        )
    }
}

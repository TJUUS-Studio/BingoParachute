package dev.bingoparachute.airdrop

import dev.bingoparachute.config.AirDropConfig
import dev.bingoparachute.model.Position3d
import dev.bingoparachute.session.AirDropPhase
import dev.bingoparachute.session.AirDropPlayerState
import dev.bingoparachute.session.AirDropSession
import net.minecraft.entity.EquipmentSlot
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.math.Vec3d
import org.slf4j.Logger
import kotlin.math.max
import kotlin.math.sqrt
import kotlin.random.Random

class ElytraCarrierHandler(
    private val log: Logger,
) : CarrierModeHandler<ServerPlayerEntity> {
    override val mode = AirDropConfig.CarrierMode.ELYTRA

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

        val finishReason = finishReason(player, config)
        if (finishReason != null) {
            finish(player, state, finishReason)
            return
        }

        if (!player.isGliding) {
            player.startGliding()
        }
        applyGlideVelocity(player, state, config)
        player.velocityModified = true
        player.fallDistance = 0.0
    }

    override fun cleanup(
        player: ServerPlayerEntity,
        state: AirDropPlayerState,
    ) {
        player.stopGliding()
        if (player.getEquippedStack(EquipmentSlot.CHEST).item == Items.ELYTRA) {
            player.equipStack(EquipmentSlot.CHEST, ItemStack.EMPTY)
        }
        player.fallDistance = 0.0
    }

    private fun startDescent(
        player: ServerPlayerEntity,
        state: AirDropPlayerState,
        tick: Long,
        config: AirDropConfig,
    ) {
        val world = player.world as ServerWorld
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
        player.equipStack(EquipmentSlot.CHEST, ItemStack(Items.ELYTRA))
        player.startGliding()
        player.fallDistance = 0.0

        log.info(
            "Started elytra descent for player {} in session {} at ({}, {}, {}) [originSource={}, activationTick={}, startedAtTick={}]",
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

    private fun applyGlideVelocity(
        player: ServerPlayerEntity,
        state: AirDropPlayerState,
        config: AirDropConfig,
    ) {
        val elytraConfig = config.elytra
        val look = player.rotationVector
        val horizontalLook = Vec3d(look.x, 0.0, look.z)
        val horizontalLength = sqrt(horizontalLook.x * horizontalLook.x + horizontalLook.z * horizontalLook.z)
        val normalizedLook = if (horizontalLength > 0.0001) {
            horizontalLook.multiply(1.0 / horizontalLength)
        } else {
            Vec3d.ZERO
        }

        val fromOriginX = player.x - state.origin.x
        val fromOriginZ = player.z - state.origin.z
        val distanceFromOrigin = sqrt(fromOriginX * fromOriginX + fromOriginZ * fromOriginZ)
        val horizontalSpeed = 0.38 * elytraConfig.glideSpeedScale
        val verticalSpeed = -minOf(0.18, elytraConfig.maxDiveSpeed * 0.25)

        val horizontalVelocity = if (distanceFromOrigin >= elytraConfig.maxHorizontalRadius) {
            val returnVector = Vec3d(state.origin.x - player.x, 0.0, state.origin.z - player.z)
            val returnLength = sqrt(returnVector.x * returnVector.x + returnVector.z * returnVector.z)
            if (returnLength > 0.0001) {
                returnVector.multiply(horizontalSpeed / returnLength)
            } else {
                Vec3d.ZERO
            }
        } else {
            normalizedLook.multiply(horizontalSpeed)
        }

        player.velocity = Vec3d(horizontalVelocity.x, verticalSpeed, horizontalVelocity.z)
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
            "Finished elytra descent for player {} in session {} (reason={})",
            player.uuid,
            state.sessionId,
            reason
        )
    }
}

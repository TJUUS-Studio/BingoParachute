package dev.bingoparachute.server

import dev.bingoparachute.BingoParachuteMod
import dev.bingoparachute.airdrop.AirDropRuntimeController
import dev.bingoparachute.pvp.PvpProtectionController
import dev.bingoparachute.session.AirDropSessionManager
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.fabricmc.fabric.api.event.player.AttackEntityCallback
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.projectile.ProjectileEntity
import net.minecraft.entity.damage.DamageSource
import net.minecraft.entity.Entity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.util.ActionResult
import java.util.UUID

class ServerEventBridge(
    private val sessionManager: AirDropSessionManager,
    private val runtimeController: AirDropRuntimeController,
    private val pvpProtectionController: PvpProtectionController,
) {
    fun initialize() {
        ServerLifecycleEvents.SERVER_STARTED.register { server ->
            BingoParachuteMod.server = server
            sessionManager.onServerStarted()
            BingoParachuteMod.log.info("Server started: {}", server.serverMotd)
        }

        ServerLifecycleEvents.SERVER_STOPPING.register { server ->
            runtimeController.onServerStopping(server)
            sessionManager.onServerStopping()
            BingoParachuteMod.server = null
        }

        ServerTickEvents.END_SERVER_TICK.register { server ->
            sessionManager.onServerTick(server.ticks.toLong())
            runtimeController.onServerTick(server)
        }

        ServerPlayConnectionEvents.JOIN.register { handler, _, _ ->
            sessionManager.onPlayerJoin(handler.player.uuid)
        }

        ServerPlayConnectionEvents.DISCONNECT.register { handler, _ ->
            runtimeController.onPlayerDisconnect(handler.player)
            sessionManager.onPlayerDisconnect(handler.player.uuid)
        }

        ServerPlayerEvents.AFTER_RESPAWN.register { oldPlayer, newPlayer, _ ->
            runtimeController.onPlayerRespawn(oldPlayer, newPlayer)
            sessionManager.onPlayerRespawn(newPlayer.uuid)
        }

        AttackEntityCallback.EVENT.register(AttackEntityCallback { player, _, _, entity, _ ->
            val target = entity as? PlayerEntity ?: return@AttackEntityCallback ActionResult.PASS
            if (pvpProtectionController.shouldBlock(player.uuid, target.uuid)) {
                ActionResult.FAIL
            } else {
                ActionResult.PASS
            }
        })

        ServerLivingEntityEvents.ALLOW_DAMAGE.register(ServerLivingEntityEvents.AllowDamage { entity, source, _ ->
            shouldAllowDamage(entity, source)
        })
    }

    private fun shouldAllowDamage(entity: LivingEntity, source: DamageSource): Boolean {
        val target = entity as? ServerPlayerEntity ?: return true
        val attackerUuid = resolveAttackerUuid(source) ?: return true
        return !pvpProtectionController.shouldBlock(attackerUuid, target.uuid)
    }

    private fun resolveAttackerUuid(source: DamageSource): UUID? {
        source.attacker?.uuid?.let { return it }

        val directSource = source.source
        if (directSource is ProjectileEntity) {
            directSource.owner?.uuid?.let { return it }
        }

        resolveOwnerUuid(directSource)?.let { return it }
        return directSource?.uuid
    }

    private fun resolveOwnerUuid(entity: Entity?): UUID? {
        if (entity == null) return null

        runCatching {
            val owner = entity.javaClass.methods
                .firstOrNull { it.name == "getOwner" && it.parameterCount == 0 }
                ?.invoke(entity) as? Entity
            owner?.uuid
        }.getOrNull()?.let { return it }

        runCatching {
            val ownerField = generateSequence(entity.javaClass as Class<*>?) { it.superclass }
                .mapNotNull { type -> type.declaredFields.firstOrNull { field -> field.name == "owner" } }
                .firstOrNull()
            ownerField?.isAccessible = true
            when (val owner = ownerField?.get(entity)) {
                is Entity -> owner.uuid
                else -> null
            }
        }.getOrNull()?.let { return it }

        return null
    }
}

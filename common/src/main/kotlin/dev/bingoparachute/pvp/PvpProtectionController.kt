package dev.bingoparachute.pvp

import dev.bingoparachute.session.AirDropSessionManager
import java.util.UUID

class PvpProtectionController(
    private val sessionManager: AirDropSessionManager,
) {
    fun isProtected(playerUuid: UUID): Boolean {
        val state = sessionManager.currentSession?.playerStates?.get(playerUuid) ?: return false
        return sessionManager.currentTick < state.pvpProtectedUntilTick
    }

    fun shouldBlock(attackerUuid: UUID, targetUuid: UUID): Boolean {
        return isProtected(attackerUuid) || isProtected(targetUuid)
    }
}

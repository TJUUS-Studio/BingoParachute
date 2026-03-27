package dev.bingoparachute.airdrop

import java.util.UUID

class PlayerLoadoutCustodian<PlayerT, SnapshotT>(
    private val adapter: PlayerLoadoutAdapter<PlayerT, SnapshotT>,
) {
    private val snapshots = mutableMapOf<UUID, SnapshotT>()

    fun captureAndClear(playerUuid: UUID, player: PlayerT) {
        if (snapshots.containsKey(playerUuid)) {
            return
        }

        snapshots[playerUuid] = adapter.capture(player)
        adapter.clear(player)
    }

    fun restore(playerUuid: UUID, player: PlayerT): Boolean {
        val snapshot = snapshots.remove(playerUuid) ?: return false
        adapter.restore(player, snapshot)
        return true
    }

    fun hasSnapshot(playerUuid: UUID): Boolean {
        return snapshots.containsKey(playerUuid)
    }

    fun clearSnapshots() {
        snapshots.clear()
    }
}

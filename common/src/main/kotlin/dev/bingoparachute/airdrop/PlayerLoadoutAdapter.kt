package dev.bingoparachute.airdrop

interface PlayerLoadoutAdapter<PlayerT, SnapshotT> {
    fun capture(player: PlayerT): SnapshotT

    fun clear(player: PlayerT)

    fun restore(player: PlayerT, snapshot: SnapshotT)
}

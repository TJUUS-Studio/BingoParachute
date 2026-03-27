package dev.bingoparachute.model

data class Position3d(
    val x: Double,
    val y: Double,
    val z: Double,
) {
    companion object {
        val ZERO = Position3d(0.0, 0.0, 0.0)
    }
}

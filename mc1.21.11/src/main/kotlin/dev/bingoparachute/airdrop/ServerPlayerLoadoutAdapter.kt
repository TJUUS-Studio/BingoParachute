package dev.bingoparachute.airdrop

import net.minecraft.item.ItemStack
import net.minecraft.server.network.ServerPlayerEntity

data class ServerPlayerLoadoutSnapshot(
    val inventoryStacks: List<ItemStack>,
)

class ServerPlayerLoadoutAdapter : PlayerLoadoutAdapter<ServerPlayerEntity, ServerPlayerLoadoutSnapshot> {
    override fun capture(player: ServerPlayerEntity): ServerPlayerLoadoutSnapshot {
        val inventory = player.inventory
        val stacks = (0 until inventory.size()).map { slot ->
            inventory.getStack(slot).copy()
        }
        return ServerPlayerLoadoutSnapshot(stacks)
    }

    override fun clear(player: ServerPlayerEntity) {
        val inventory = player.inventory
        for (slot in 0 until inventory.size()) {
            inventory.setStack(slot, ItemStack.EMPTY)
        }
        player.currentScreenHandler.cursorStack = ItemStack.EMPTY
        player.playerScreenHandler.cursorStack = ItemStack.EMPTY
        player.currentScreenHandler.sendContentUpdates()
    }

    override fun restore(player: ServerPlayerEntity, snapshot: ServerPlayerLoadoutSnapshot) {
        val inventory = player.inventory
        for (slot in 0 until inventory.size()) {
            val stack = snapshot.inventoryStacks.getOrNull(slot)?.copy() ?: ItemStack.EMPTY
            inventory.setStack(slot, stack)
        }
        player.currentScreenHandler.cursorStack = ItemStack.EMPTY
        player.playerScreenHandler.cursorStack = ItemStack.EMPTY
        player.currentScreenHandler.sendContentUpdates()
    }
}

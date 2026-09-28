package com.mengsama.mod.mengsamanetmusic.gui;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

 
final class HiddenPlayerInventorySlot extends Slot {
    HiddenPlayerInventorySlot(Container inventory, int index) {
        super(inventory, index, -10000, -10000);
    }

    @Override public boolean isActive() { return false; }
    @Override public boolean mayPlace(@NotNull ItemStack stack) { return false; }
    @Override public boolean mayPickup(@NotNull Player player) { return false; }
}

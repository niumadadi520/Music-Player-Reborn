package com.mengsama.mod.mengsamanetmusic.compat;

import com.mengsama.mod.mengsamanetmusic.item.MusicPlayerItem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import java.util.UUID;

 
public final class PortableDevices {
    private PortableDevices() {}
    public static boolean matches(ItemStack stack, UUID id) {
        return id != null && stack.getItem() instanceof MusicPlayerItem && id.equals(MusicPlayerItem.getInstanceId(stack));
    }
    public static ItemStack match(Iterable<ItemStack> stacks, UUID id) {
        for (ItemStack stack : stacks) if (matches(stack, id)) return stack;
        return ItemStack.EMPTY;
    }
    public static ItemStack find(Player player, UUID id, boolean includeCursor) {
        if (player == null || id == null) return ItemStack.EMPTY;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (matches(stack, id)) return stack;
        }
        if (includeCursor && matches(player.containerMenu.getCarried(), id)) return player.containerMenu.getCarried();
        return BackpackAccess.find(player, id);
    }
    public static ItemStack controllable(Player player, UUID id) {
        if (matches(player.getMainHandItem(), id)) return player.getMainHandItem();
        if (matches(player.getOffhandItem(), id)) return player.getOffhandItem();
        return ItemStack.EMPTY;
    }
    public static String attachment(Player player, ItemStack stack) {
        UUID id = MusicPlayerItem.getInstanceId(stack);
        if (matches(player.getMainHandItem(), id) || matches(player.getOffhandItem(), id)) return "hand";
        for (int i = 0; i < player.getInventory().getContainerSize(); i++)
            if (matches(player.getInventory().getItem(i), id)) return "pocket";
        return !BackpackAccess.find(player, id).isEmpty() ? "backpack" : "pocket";
    }
}

package com.mengsama.mod.mengsamanetmusic.item;

import net.neoforged.fml.common.EventBusSubscriber;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.item.ItemTossEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;

 
@EventBusSubscriber(modid = "mengsamanetmusic")
public final class DroppedMusicDeviceHandler {
    private DroppedMusicDeviceHandler() {}
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void tossed(ItemTossEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) return;
        var stack = event.getEntity().getItem();
        if (stack.getItem() instanceof MusicPlayerItem) MusicPlayerItem.stopDroppedDevice(stack, player);
    }
}

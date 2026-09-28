package com.mengsama.mod.mengsamanetmusic.item;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

 
@Mod.EventBusSubscriber(modid = "mengsamanetmusic")
public final class DroppedMusicDeviceHandler {
    private DroppedMusicDeviceHandler() {}
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void tossed(ItemTossEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) return;
        var stack = event.getEntity().getItem();
        if (stack.getItem() instanceof MusicPlayerItem) MusicPlayerItem.stopDroppedDevice(stack, player);
    }
}

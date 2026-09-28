package com.mengsama.mod.mengsamanetmusic.karaoke;

import net.minecraft.world.InteractionHand;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

 
@Mod.EventBusSubscriber(modid="mengsamanetmusic")
public final class KaraokeInteraction {
    private KaraokeInteraction() {}
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void rightClick(PlayerInteractEvent.RightClickBlock event) {
        if(event.getHand()!=InteractionHand.MAIN_HAND || !event.getEntity().isShiftKeyDown()
                || event.getEntity().isSpectator() || event.getUseBlock()==Event.Result.DENY)return;
        var block=event.getLevel().getBlockState(event.getPos()).getBlock();
        if(block instanceof KaraokeDeviceBlock || block instanceof KaraokeStandTopBlock) {
            event.setUseBlock(Event.Result.ALLOW);
            event.setUseItem(Event.Result.DENY);
        }
    }
}

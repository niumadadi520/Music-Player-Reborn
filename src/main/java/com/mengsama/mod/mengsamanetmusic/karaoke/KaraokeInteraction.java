package com.mengsama.mod.mengsamanetmusic.karaoke;

import net.neoforged.fml.common.EventBusSubscriber;
import net.minecraft.world.InteractionHand;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;

 
@EventBusSubscriber(modid="mengsamanetmusic")
public final class KaraokeInteraction {
    private KaraokeInteraction() {}
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void rightClick(PlayerInteractEvent.RightClickBlock event) {
        if(event.getHand()!=InteractionHand.MAIN_HAND || !event.getEntity().isShiftKeyDown()
                || event.getEntity().isSpectator() || event.getUseBlock()==net.neoforged.neoforge.common.util.TriState.FALSE)return;
        var block=event.getLevel().getBlockState(event.getPos()).getBlock();
        if(block instanceof KaraokeDeviceBlock || block instanceof KaraokeStandTopBlock) {
            event.setUseBlock(net.neoforged.neoforge.common.util.TriState.TRUE);
            event.setUseItem(net.neoforged.neoforge.common.util.TriState.FALSE);
        }
    }
}

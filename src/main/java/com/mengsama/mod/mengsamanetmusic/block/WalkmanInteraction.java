package com.mengsama.mod.mengsamanetmusic.block;

import net.neoforged.fml.common.EventBusSubscriber;
import com.mengsama.mod.mengsamanetmusic.MengSamaNetMusic;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;

@EventBusSubscriber(modid = MengSamaNetMusic.MOD_ID)
public final class WalkmanInteraction {
    private WalkmanInteraction() {}

     
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void rightClick(PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand() != InteractionHand.MAIN_HAND || !event.getEntity().isShiftKeyDown()
                || event.getEntity().isSpectator() || event.getUseBlock() == net.neoforged.neoforge.common.util.TriState.FALSE) return;
        var state = event.getLevel().getBlockState(event.getPos());
        if (!(state.getBlock() instanceof PortableMusicPlayerBlock)) return;
        var hit = event.getHitVec();
        if (WalkmanControl.at(state.getValue(PortableMusicPlayerBlock.FACING), hit.getDirection(),
                hit.getLocation().subtract(Vec3.atLowerCornerOf(event.getPos()))) != null
                || event.getEntity().getMainHandItem().getItem() instanceof com.mengsama.mod.mengsamanetmusic.item.MusicListItem) {
            event.setUseBlock(net.neoforged.neoforge.common.util.TriState.TRUE);
            event.setUseItem(net.neoforged.neoforge.common.util.TriState.FALSE);
        }
    }
}

package com.mengsama.mod.mengsamanetmusic.block;

import com.mengsama.mod.mengsamanetmusic.MengSamaNetMusic;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = MengSamaNetMusic.MOD_ID)
public final class WalkmanInteraction {
    private WalkmanInteraction() {}

     
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void rightClick(PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand() != InteractionHand.MAIN_HAND || !event.getEntity().isShiftKeyDown()
                || event.getEntity().isSpectator() || event.getUseBlock() == Event.Result.DENY) return;
        var state = event.getLevel().getBlockState(event.getPos());
        if (!(state.getBlock() instanceof PortableMusicPlayerBlock)) return;
        var hit = event.getHitVec();
        if (WalkmanControl.at(state.getValue(PortableMusicPlayerBlock.FACING), hit.getDirection(),
                hit.getLocation().subtract(Vec3.atLowerCornerOf(event.getPos()))) != null
                || event.getEntity().getMainHandItem().getItem() instanceof com.mengsama.mod.mengsamanetmusic.item.MusicListItem) {
            event.setUseBlock(Event.Result.ALLOW);
            event.setUseItem(Event.Result.DENY);
        }
    }
}

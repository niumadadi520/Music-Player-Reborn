package com.mengsama.mod.mengsamanetmusic.client;

import com.mengsama.mod.mengsamanetmusic.MengSamaNetMusic;
import com.mengsama.mod.mengsamanetmusic.block.PortableMusicPlayerBlock;
import com.mengsama.mod.mengsamanetmusic.block.WalkmanControl;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = MengSamaNetMusic.MOD_ID, value = Dist.CLIENT)
public final class WalkmanButtonHint {
    @SubscribeEvent public static void render(RenderGuiEvent.Post event) {
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.screen != null || mc.options.hideGui
                || !(mc.hitResult instanceof BlockHitResult hit)) return;
        var state = mc.level.getBlockState(hit.getBlockPos());
        if (!(state.getBlock() instanceof PortableMusicPlayerBlock)) return;
        var control = WalkmanControl.at(state.getValue(PortableMusicPlayerBlock.FACING), hit.getDirection(),
                hit.getLocation().subtract(Vec3.atLowerCornerOf(hit.getBlockPos())));
        if (control != null) event.getGuiGraphics().drawCenteredString(mc.font, "Shift + 右键：" + control.label,
                mc.getWindow().getGuiScaledWidth() / 2, mc.getWindow().getGuiScaledHeight() / 2 + 16,
                mc.player.isShiftKeyDown() ? 0xFFFFC9D5 : 0xFFFFFFFF);
    }
}

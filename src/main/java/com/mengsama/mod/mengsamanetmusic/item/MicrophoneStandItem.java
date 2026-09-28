package com.mengsama.mod.mengsamanetmusic.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import java.util.List;

 
public final class MicrophoneStandItem extends MusicDeviceBlockItem {
    public MicrophoneStandItem(Block block) {
        super(block, new Properties().stacksTo(1), "pink_microphone_stand");
    }

    @Override public void appendHoverText(ItemStack stack, Level level, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("tooltip.mengsamanetmusic.microphone_stand.mount"));
        lines.add(Component.translatable("tooltip.mengsamanetmusic.microphone_stand.remove"));
        lines.add(Component.translatable("tooltip.mengsamanetmusic.microphone_stand.settings"));
    }
}

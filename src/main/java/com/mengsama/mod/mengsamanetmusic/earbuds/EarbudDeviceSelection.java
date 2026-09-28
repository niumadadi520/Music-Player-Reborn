package com.mengsama.mod.mengsamanetmusic.earbuds;

import com.mengsama.mod.mengsamanetmusic.item.MusicPlayerItem;
import net.minecraft.world.item.ItemStack;

 
public final class EarbudDeviceSelection {
    private EarbudDeviceSelection() {}
    public static ItemStack choose(ItemStack selected, ItemStack main, ItemStack off, ItemStack backpack) {
         
        if (EarbudSlots.installed(selected) && MusicPlayerItem.isPlay(selected)) return selected;
        if (EarbudSlots.installed(main)) return main;
        if (EarbudSlots.installed(off)) return off;
        return EarbudSlots.installed(backpack) ? backpack : ItemStack.EMPTY;
    }
}

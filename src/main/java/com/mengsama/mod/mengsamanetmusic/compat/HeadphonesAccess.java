package com.mengsama.mod.mengsamanetmusic.compat;

import com.mengsama.mod.mengsamanetmusic.item.PinkHeadphonesItem;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.fml.ModList;

public final class HeadphonesAccess {
    private HeadphonesAccess() {}
    public static boolean isWearing(LivingEntity wearer) {
        if (wearer == null) return false;
        if (wearer.getItemBySlot(EquipmentSlot.HEAD).getItem() instanceof PinkHeadphonesItem) return true;
        return ModList.get() != null && ModList.get().isLoaded("curios") && CuriosHeadphonesCompat.isWearing(wearer);
    }
}

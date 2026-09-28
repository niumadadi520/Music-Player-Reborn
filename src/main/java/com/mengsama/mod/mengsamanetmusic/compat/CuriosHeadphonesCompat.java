package com.mengsama.mod.mengsamanetmusic.compat;

import com.mengsama.mod.mengsamanetmusic.init.ModItems;
import com.mengsama.mod.mengsamanetmusic.item.PinkHeadphonesItem;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

 
public final class CuriosHeadphonesCompat {
    private CuriosHeadphonesCompat() {}
    public static void register() {
        CuriosApi.registerCurio(ModItems.PINK_HEADPHONES.get(), new ICurioItem() {
            @Override public boolean canEquip(SlotContext context, ItemStack stack) {
                return context.identifier().equals("head");
            }
        });
    }
    public static boolean isWearing(LivingEntity entity) {
        return CuriosApi.getCuriosInventory(entity).map(inventory -> {
            var head = inventory.getCurios().get("head");
            if (head == null) return false;
            var stacks = head.getStacks();
            for (int i = 0; i < stacks.getSlots(); i++)
                if (stacks.getStackInSlot(i).getItem() instanceof PinkHeadphonesItem) return true;
            return false;
        }).orElse(false);
    }
}

package com.mengsama.mod.mengsamanetmusic.item;
import java.util.List;
import java.util.Map;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.sounds.SoundEvents;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;
 
public final class HeadphonesMaterial {
    public static final DeferredRegister<ArmorMaterial> MATERIALS=DeferredRegister.create(Registries.ARMOR_MATERIAL,"mengsamanetmusic");
    public static final DeferredHolder<ArmorMaterial,ArmorMaterial> INSTANCE=MATERIALS.register("pink_headphones",()->new ArmorMaterial(Map.of(ArmorItem.Type.HELMET,0),0,SoundEvents.ARMOR_EQUIP_LEATHER,()->Ingredient.EMPTY,List.of(),0,0));
    private HeadphonesMaterial() {}
}

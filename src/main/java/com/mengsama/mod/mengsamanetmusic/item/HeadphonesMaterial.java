package com.mengsama.mod.mengsamanetmusic.item;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.crafting.Ingredient;

 
public enum HeadphonesMaterial implements ArmorMaterial {
    INSTANCE;
    public int getDurabilityForType(ArmorItem.Type type) { return 0; }
    public int getDefenseForType(ArmorItem.Type type) { return 0; }
    public int getEnchantmentValue() { return 0; }
    public SoundEvent getEquipSound() { return SoundEvents.ARMOR_EQUIP_LEATHER; }
    public Ingredient getRepairIngredient() { return Ingredient.EMPTY; }
    public String getName() { return "mengsamanetmusic:pink_headphones"; }
    public float getToughness() { return 0; }
    public float getKnockbackResistance() { return 0; }
}

package com.mengsama.mod.mengsamanetmusic.platform;

import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

 
public final class ItemData {
    private ItemData() {}
    public static boolean has(ItemStack stack) { return stack.has(DataComponents.CUSTOM_DATA); }
    public static CompoundTag get(ItemStack stack) { return stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag(); }
    public static CompoundTag nullable(ItemStack stack) { return has(stack) ? get(stack) : null; }
    public static CompoundTag child(ItemStack stack,String name) { var tag=get(stack);return tag.contains(name,Tag.TAG_COMPOUND)?tag.getCompound(name):null; }
    public static CompoundTag childOrEmpty(ItemStack stack,String name) { return get(stack).getCompound(name); }
    public static void set(ItemStack stack,CompoundTag tag) {
        if(tag==null || tag.isEmpty())stack.remove(DataComponents.CUSTOM_DATA);
        else stack.set(DataComponents.CUSTOM_DATA,CustomData.of(tag.copy()));
    }
    public static void update(ItemStack stack,Consumer<CompoundTag> mutation) { var data=get(stack);mutation.accept(data);set(stack,data); }
    public static void put(ItemStack stack,String key,Tag value) { update(stack,data->data.put(key,value.copy())); }
    public static void putInt(ItemStack stack,String key,int value) { update(stack,data->data.putInt(key,value)); }
    public static void putBoolean(ItemStack stack,String key,boolean value) { update(stack,data->data.putBoolean(key,value)); }
    public static void putUUID(ItemStack stack,String key,UUID value) { update(stack,data->data.putUUID(key,value)); }
    public static void putString(ItemStack stack,String key,String value) { update(stack,data->data.putString(key,value)); }
    public static void putLong(ItemStack stack,String key,long value) { update(stack,data->data.putLong(key,value)); }
    public static void remove(ItemStack stack,String key) { update(stack,data->data.remove(key)); }
}

package com.mengsama.mod.mengsamanetmusic.platform;

import com.mojang.serialization.Dynamic;
import net.minecraft.SharedConstants;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.*;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.util.datafix.fixes.References;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.item.ItemStack;

 
public final class StoredItems {
    private StoredItems() {}
    public static CompoundTag upgrade(CompoundTag original) {
        if (!original.contains("Count") || original.contains("count")) return original.copy();
        var fixed=DataFixers.getDataFixer().update(References.ITEM_STACK,
                new Dynamic<>(NbtOps.INSTANCE,original.copy()),3465,
                SharedConstants.getCurrentVersion().getDataVersion().getVersion()).getValue();
        if (!(fixed instanceof CompoundTag result)) throw new IllegalStateException("Item data migration did not produce a compound");
        if(original.contains("Slot"))result.put("Slot",original.get("Slot").copy());
        return result;
    }
    public static CompoundTag upgradeInventory(CompoundTag original) {
        var result=original.copy();var items=new ListTag();
        for(var entry:original.getList("Items",Tag.TAG_COMPOUND))items.add(upgrade((CompoundTag)entry));
        if(original.contains("Items"))result.put("Items",items);
        return result;
    }
    public static ItemStack read(HolderLookup.Provider registries,CompoundTag tag) {
        return ItemStack.parseOptional(registries,upgrade(tag));
    }
    public static void loadAll(CompoundTag tag,NonNullList<ItemStack> items,HolderLookup.Provider registries) {
        ContainerHelper.loadAllItems(upgradeInventory(tag),items,registries);
    }
}

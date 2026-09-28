package com.mengsama.mod.mengsamanetmusic.karaoke;

import com.mengsama.mod.mengsamanetmusic.item.MusicPlayerItem;
import com.mengsama.mod.mengsamanetmusic.util.PlayMode;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.ItemStackHandler;

import java.util.UUID;

 
public final class KaraokeMicrophoneData {
    private static final String[] DEVICE_KEYS = {"Item", "ItemStacksCD", "PlayIndex", "PlayMode", "IsPlay", "IsPaused",
            "CurrentTime", "AutoAdvanceArmed", "KaraokeDeviceId", "KaraokeMicrophoneId", "MusicPlayerInstanceId",
            "KaraokeConnections", "KaraokeVolume", "KaraokeRuntimeActive", "KaraokeVoiceUntil",
            "KaraokeMountedMicrophone", "KaraokeStandItem", "KaraokeItemExtras"};
    private KaraokeMicrophoneData() {}

    public static boolean isHandheld(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof KaraokeMicrophoneItem microphone && microphone.isHandheld();
    }

    public static NonNullList<ItemStack> playlist(ItemStack stack) {
        if (stack.getTagElement("Item") != null) return MusicPlayerItem.loadAllCds(stack);
         
        NonNullList<ItemStack> items = NonNullList.withSize(54, ItemStack.EMPTY);
        CompoundTag tag = stack.getTagElement("KaraokeBlockData");
        if (tag != null) for (var entry : tag.getCompound("ItemStacksCD").getList("Items", 10)) {
            CompoundTag item = (CompoundTag) entry;
            int slot = item.getInt("Slot");
            if (slot >= 0 && slot < items.size()) items.set(slot, ItemStack.of(item));
        }
        return items;
    }

    public static int playIndex(ItemStack stack) {
        return stack.hasTag() && stack.getTag().contains("PlayIndex", 99) ? MusicPlayerItem.getPlayIndex(stack)
                : stack.getTagElement("KaraokeBlockData") == null ? 0 : stack.getTagElement("KaraokeBlockData").getInt("PlayIndex");
    }
    public static PlayMode playMode(ItemStack stack) {
        return stack.hasTag() && stack.getTag().contains("PlayMode", 99) ? MusicPlayerItem.getPlayMode(stack)
                : stack.getTagElement("KaraokeBlockData") == null ? PlayMode.SEQUENTIAL
                : PlayMode.getMode(stack.getTagElement("KaraokeBlockData").getInt("PlayMode"));
    }

    public static ItemStack snapshot(ItemStack template, UUID id, ItemStackHandler inventory, int selected, PlayMode mode) {
        if (template.isEmpty()) return ItemStack.EMPTY;
        ItemStack result = template.copy();
        result.setCount(1);
        NonNullList<ItemStack> contents = NonNullList.withSize(54, ItemStack.EMPTY);
        for (int i = 0; i < Math.min(contents.size(), inventory.getSlots()); i++) contents.set(i, inventory.getStackInSlot(i).copy());
        int original = Math.max(0, Math.min(contents.size() - 1, selected));
        int compact = 0;
        if (!contents.get(original).isEmpty()) for (int i = 0; i < original; i++) if (!contents.get(i).isEmpty()) compact++;
        MusicPlayerItem.saveAllCdsToItem(result, contents);
        MusicPlayerItem.setPlayIndex(result, compact);
        MusicPlayerItem.setPlayMode(result, mode);
        MusicPlayerItem.setPlay(result, false);
        MusicPlayerItem.setPaused(result, false);
        MusicPlayerItem.setCurrentTime(result, 0);
        CompoundTag root = result.getOrCreateTag();
        root.remove("AutoAdvanceArmed");
        root.remove("KaraokeRuntimeActive");
        root.remove("KaraokeVoiceUntil");
        if (id != null) root.putUUID(KaraokeMicrophoneItem.ID_TAG, id);
        if (root.contains("KaraokeBlockData", 10)) {
            CompoundTag legacy = root.getCompound("KaraokeBlockData").copy();
            ItemStackHandler packed = new ItemStackHandler(54);
            var items = MusicPlayerItem.loadAllCds(result);
            for (int i = 0; i < 54; i++) packed.setStackInSlot(i, items.get(i).copy());
            CompoundTag inventoryTag = legacy.getCompound("ItemStacksCD").copy();
            inventoryTag.merge(packed.serializeNBT());
            legacy.put("ItemStacksCD", inventoryTag);
            legacy.putInt("PlayIndex", compact);
            legacy.putInt("PlayMode", mode.ordinal());
            legacy.putBoolean("IsPlay", false); legacy.putBoolean("IsPaused", false); legacy.putInt("CurrentTime", 0);
            legacy.remove("KaraokeRuntimeActive"); legacy.remove("KaraokeVoiceUntil");
            legacy.remove("KaraokeMountedMicrophone"); legacy.remove("KaraokeStandItem");
            if (id != null) legacy.putUUID("KaraokeDeviceId", id);
            root.put("KaraokeBlockData", legacy);
        }
        return result;
    }

     
    public static ItemStack standOnly(ItemStack source) {
        if (source.isEmpty()) return ItemStack.EMPTY;
        ItemStack result = source.copy(); result.setCount(1);
        if (result.hasTag()) {
            CompoundTag tag = result.getTag();
            for (String key : DEVICE_KEYS) tag.remove(key);
            for (String nested : new String[]{"KaraokeBlockData", "BlockEntityTag"}) if (tag.contains(nested, 10)) {
                CompoundTag clean = tag.getCompound(nested).copy();
                for (String key : DEVICE_KEYS) clean.remove(key);
                if (clean.isEmpty()) tag.remove(nested); else tag.put(nested, clean);
            }
        }
        return result;
    }
}

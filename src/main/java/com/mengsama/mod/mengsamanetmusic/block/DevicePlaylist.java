package com.mengsama.mod.mengsamanetmusic.block;

import com.mengsama.mod.mengsamanetmusic.item.MusicListItem;
import com.mengsama.mod.mengsamanetmusic.util.ManualTrackNavigation;
import com.mengsama.mod.mengsamanetmusic.util.PlayMode;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;
import java.util.function.IntUnaryOperator;

 
public final class DevicePlaylist {
    public static final int CAPACITY = 54;
    private final ItemStackHandler storage;
    private CompoundTag inventoryEnvelope = new CompoundTag();
    private int selected;
    private PlayMode order = PlayMode.SEQUENTIAL;
    private final Runnable changed;

    public DevicePlaylist(Runnable changed) {
        this.changed = changed;
        storage = new ItemStackHandler(CAPACITY) {
            @Override public boolean isItemValid(int slot, ItemStack item) { return item.getItem() instanceof MusicListItem; }
            @Override protected int getStackLimit(int slot, ItemStack item) { return 1; }
            @Override protected void onContentsChanged(int slot) { changed.run(); }
        };
    }
    public ItemStackHandler inventory() { return storage; }
    public int selection() { return selected; }
    public PlayMode order() { return order; }
    public void select(int value) { selected = bounded(value); changed.run(); }
    public void order(PlayMode value) { order = value == null ? PlayMode.SEQUENTIAL : value; changed.run(); }
    private int bounded(int index) { return Math.max(0, Math.min(Math.min(CAPACITY, storage.getSlots()) - 1, index)); }

    public ItemStack current() {
        if (storage.getSlots() == 0) return ItemStack.EMPTY;
        selected = bounded(selected);
        if (storage.getStackInSlot(selected).isEmpty()) {
            for (int slot = 0; slot < Math.min(CAPACITY, storage.getSlots()); slot++) {
                if (storage.getStackInSlot(slot).isEmpty()) continue;
                selected = slot;
                break;
            }
        }
        return storage.getStackInSlot(selected);
    }
    public void advance(IntUnaryOperator random) { navigate(0, random); }
    public void manual(int direction) { navigate(direction, ignored -> 0); }
    private void navigate(int direction, IntUnaryOperator random) {
        int[] counts = new int[Math.min(CAPACITY, storage.getSlots())];
        for (int slot = 0; slot < counts.length; slot++) {
            ItemStack item = storage.getStackInSlot(slot);
            counts[slot] = item.getItem() instanceof MusicListItem ? MusicListItem.getSongCount(item) : item.isEmpty() ? 0 : 1;
        }
        if (counts.length == 0) return;
        ItemStack old = storage.getStackInSlot(bounded(selected));
        int song = MusicListItem.getSongIndex(old);
        PlayMode.TrackPosition next = direction == 0 ? PlayMode.nextTrack(order, selected, song, counts, random)
                : ManualTrackNavigation.move(selected, song, counts, direction);
        selected = bounded(next.slotIndex());
        MusicListItem.setSongIndex(storage.getStackInSlot(selected), next.songIndex());
        changed.run();
    }
    public void read(CompoundTag tag) { read(tag, com.mengsama.mod.mengsamanetmusic.platform.GameRegistries.lookup()); }
    public void read(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        inventoryEnvelope = tag.getCompound("ItemStacksCD").copy();
        if (tag.contains("ItemStacksCD", net.minecraft.nbt.Tag.TAG_COMPOUND)) {
            CompoundTag inventory = tag.getCompound("ItemStacksCD").copy();
             
            if (!inventory.contains("Size") || inventory.getInt("Size") < CAPACITY) inventory.putInt("Size", CAPACITY);
            storage.deserializeNBT(registries, com.mengsama.mod.mengsamanetmusic.platform.StoredItems.upgradeInventory(inventory));
        } else storage.setSize(CAPACITY);
        selected = bounded(tag.getInt("PlayIndex"));
        order = tag.contains("PlayMode") ? PlayMode.getMode(tag.getInt("PlayMode")) : PlayMode.SEQUENTIAL;
    }
    public void write(CompoundTag tag) { write(tag, com.mengsama.mod.mengsamanetmusic.platform.GameRegistries.lookup()); }
    public void write(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        CompoundTag inventory = inventoryEnvelope.copy();
        inventory.merge(storage.serializeNBT(registries));
        tag.put("ItemStacksCD", inventory);
        tag.putInt("PlayIndex", selected);
        tag.putInt("PlayMode", order.ordinal());
    }
}

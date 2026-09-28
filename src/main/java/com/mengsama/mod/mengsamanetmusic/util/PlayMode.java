package com.mengsama.mod.mengsamanetmusic.util;

import net.minecraft.network.chat.Component;

import java.util.function.IntUnaryOperator;

public enum PlayMode {
    LOOP, SEQUENTIAL, RANDOM;

     
    public record TrackPosition(int slotIndex, int songIndex) { }

    public Component getName() {
        return Component.translatable("button.mengsamanetmusic." + this.name().toLowerCase(java.util.Locale.ROOT));
    }

    private static final PlayMode[] SAVED_ORDER = {LOOP, SEQUENTIAL, RANDOM};
    public PlayMode getNext() { return SAVED_ORDER[(ordinal() + 1) % SAVED_ORDER.length]; }
    public static PlayMode getMode(Integer stored) {
        return stored != null && stored >= 0 && stored < SAVED_ORDER.length ? SAVED_ORDER[stored] : LOOP;
    }

     



    public static TrackPosition nextTrack(PlayMode mode, int currentSlot, int currentSong,
                                          int[] songCounts, IntUnaryOperator randomIndex) {
        if (songCounts == null || songCounts.length == 0) return new TrackPosition(currentSlot, currentSong);
        int total = 0;
        int normalizedCurrent = -1;
        for (int slot = 0; slot < songCounts.length; slot++) {
            int count = Math.max(0, songCounts[slot]);
            if (slot == currentSlot && count > 0) {
                int song = Math.max(0, Math.min(currentSong, count - 1));
                normalizedCurrent = total + song;
            }
            total += count;
        }
        if (total == 0) return new TrackPosition(currentSlot, currentSong);
        if (normalizedCurrent < 0) normalizedCurrent = 0;

        int selected = normalizedCurrent;
        if (mode == SEQUENTIAL) {
            selected = (normalizedCurrent + 1) % total;
        } else if (mode == RANDOM && total > 1) {
            int candidate = Math.floorMod(randomIndex.applyAsInt(total - 1), total - 1);
            selected = candidate >= normalizedCurrent ? candidate + 1 : candidate;
        }

        for (int slot = 0; slot < songCounts.length; slot++) {
            int count = Math.max(0, songCounts[slot]);
            if (selected < count) return new TrackPosition(slot, selected);
            selected -= count;
        }
        return new TrackPosition(currentSlot, currentSong);
    }
}

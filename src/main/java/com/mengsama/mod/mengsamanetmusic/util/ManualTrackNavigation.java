package com.mengsama.mod.mengsamanetmusic.util;

 
public final class ManualTrackNavigation {
    private ManualTrackNavigation() {}
    public static PlayMode.TrackPosition move(int slot, int song, int[] counts, int direction) {
        int total = 0, current = -1;
        for (int i = 0; i < counts.length; i++) {
            int count = Math.max(0, counts[i]);
            if (i == slot && count > 0) current = total + Math.max(0, Math.min(song, count - 1));
            total += count;
        }
        if (total == 0) return new PlayMode.TrackPosition(Math.max(0, slot), 0);
        int selected = current < 0 ? (direction < 0 ? total - 1 : 0)
                : Math.floorMod(current + (direction < 0 ? -1 : 1), total);
        for (int i = 0; i < counts.length; i++) {
            int count = Math.max(0, counts[i]);
            if (selected < count) return new PlayMode.TrackPosition(i, selected);
            selected -= count;
        }
        throw new IllegalStateException("Invalid flattened playlist");
    }
}

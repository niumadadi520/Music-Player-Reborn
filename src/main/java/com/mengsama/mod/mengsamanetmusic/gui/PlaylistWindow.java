package com.mengsama.mod.mengsamanetmusic.gui;

import com.mengsama.mod.mengsamanetmusic.api.SongInfo;
import com.mengsama.mod.mengsamanetmusic.util.PlaylistFilter;
import java.util.List;

 
final class PlaylistWindow {
    record Page(List<PlaylistFilter.Match> rows, double scroll) {}
    private PlaylistWindow() {}
    static Page filter(List<SongInfo> songs, List<Integer> slots, String query, double fallback,
                       int anchorSlot, double offset, int rowHeight) {
        List<PlaylistFilter.Match> rows = PlaylistFilter.filter(songs, slots, query);
        int anchor = -1;
        for (int i = 0; i < rows.size(); i++) if (rows.get(i).slotIndex() == anchorSlot) anchor = i;
        return new Page(rows, Math.max(0, anchor >= 0 ? anchor * rowHeight + offset : fallback));
    }
}

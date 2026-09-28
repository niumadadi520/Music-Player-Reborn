package com.mengsama.mod.mengsamanetmusic.util;

import com.mengsama.mod.mengsamanetmusic.api.SongInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

 
public final class PlaylistFilter {
    private PlaylistFilter() {}

    public record Match(SongInfo song, int slotIndex) {}

    public static List<Match> filter(List<SongInfo> songs, List<Integer> slots, String query) {
        List<Match> result = new ArrayList<>();
        String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        for (int i = 0; i < Math.min(songs.size(), slots.size()); i++) {
            SongInfo song = songs.get(i);
            if (needle.isEmpty() || searchableText(song).contains(needle)) {
                result.add(new Match(song, slots.get(i)));
            }
        }
        return result;
    }

    static String searchableText(SongInfo song) {
        if (song == null) return "";
        String artists = song.artists == null ? "" : String.join(" ", song.artists);
        return ((song.songName == null ? "" : song.songName) + "\n" + artists + "\n"
                + (song.albumName == null ? "" : song.albumName)).toLowerCase(Locale.ROOT);
    }
}

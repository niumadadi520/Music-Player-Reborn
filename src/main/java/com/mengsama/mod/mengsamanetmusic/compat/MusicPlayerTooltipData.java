package com.mengsama.mod.mengsamanetmusic.compat;

import com.mengsama.mod.mengsamanetmusic.api.SongInfo;

import java.net.URI;
import java.text.Normalizer;
import java.util.List;

 
public record MusicPlayerTooltipData(boolean playing, boolean paused, String title, String artists,
                                     String album, String coverUrl, String targetId, SongInfo song) {
    private static final int MAX_TEXT_LENGTH = 160;

    public static MusicPlayerTooltipData empty() {
        return new MusicPlayerTooltipData(false, false, "", "", "", "", "", null);
    }

    public static MusicPlayerTooltipData of(boolean playing, boolean paused, SongInfo song, String targetId) {
        if (!playing || song == null) return empty();
        SongInfo stableSong = song.clone();
        stableSong.songUrl = stableSong.rawUrl == null ? "" : stableSong.rawUrl;
        stableSong.playbackHeaders.clear();
        return new MusicPlayerTooltipData(true, paused, clean(song.songName), joinArtists(song.artists),
                clean(song.albumName), cleanCoverUrl(song.preferredCoverUrl()), clean(targetId), stableSong);
    }

     
    public static MusicPlayerTooltipData of(boolean playing, boolean paused, SongInfo song) {
        return of(playing, paused, song, "");
    }

    static String joinArtists(List<String> artists) {
        if (artists == null || artists.isEmpty()) return "";
        return artists.stream().map(MusicPlayerTooltipData::clean).filter(value -> !value.isEmpty())
                .distinct().reduce((left, right) -> left + " / " + right).orElse("");
    }

    static String clean(String value) {
        if (value == null) return "";
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFC)
                .replaceAll("(?i)§[0-9A-FK-OR]", "")
                .replaceAll("[\\p{Cntrl}\\p{Cf}]", "")
                .replace('\uFFFD', ' ')
                .replaceAll("\\s+", " ")
                .trim();
        return normalized.length() <= MAX_TEXT_LENGTH ? normalized : normalized.substring(0, MAX_TEXT_LENGTH);
    }

    static String cleanCoverUrl(String value) {
        if (value == null) return "";
        String cleaned = Normalizer.normalize(value, Normalizer.Form.NFC)
                .replaceAll("[\\p{Cntrl}\\p{Cf}]", "").trim();
        if (cleaned.isEmpty() || cleaned.length() > 2_048) return "";
        try {
            URI uri = URI.create(cleaned);
            String scheme = uri.getScheme();
            return uri.getHost() != null && ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
                    ? cleaned : "";
        } catch (IllegalArgumentException ignored) {
            return "";
        }
    }
}

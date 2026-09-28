package com.mengsama.mod.mengsamanetmusic.gui;

 
public final class SongRowLayout {
    public record Columns(int title, int artist, int album, int gap) {
        public int total() { return title + artist + album + gap * 2; }
    }

    private SongRowLayout() {}

    public static Columns allocate(int available, int titleWanted, int artistWanted, int albumWanted) {
        int width = Math.max(0, available);
        int gap = width >= 24 ? 4 : 0;
        int content = Math.max(0, width - gap * 2);
        int[] wanted = {Math.max(0, titleWanted), Math.max(0, artistWanted), Math.max(0, albumWanted)};
        int[] result = {0, 0, 0};
        if (content == 0) return new Columns(0, 0, 0, gap);

         
         
        int minimum = Math.min(18, content / 3);
        for (int i = 0; i < 3; i++) result[i] = Math.min(wanted[i], minimum);
        int remaining = content - result[0] - result[1] - result[2];
        while (remaining > 0) {
            int best = -1;
            double bestNeed = 0;
            for (int i = 0; i < 3; i++) {
                double need = wanted[i] - result[i];
                if (need > bestNeed) { bestNeed = need; best = i; }
            }
            if (best < 0) break;
            result[best]++;
            remaining--;
        }
         
        result[0] += remaining;
        return new Columns(result[0], result[1], result[2], gap);
    }

    public static String value(String text, String fallback) {
        return text == null || text.isBlank() ? fallback : text;
    }
}

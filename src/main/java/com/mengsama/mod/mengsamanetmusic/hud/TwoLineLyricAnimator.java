package com.mengsama.mod.mengsamanetmusic.hud;

import com.mengsama.mod.mengsamanetmusic.client.lyric.LyricRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

 
public final class TwoLineLyricAnimator {
    public static final long TRANSITION_NANOS = 260_000_000L;
    private static final long SEEK_DELTA_MILLIS = 350L;
    private String target;
    private String identity;
    private LyricRepository.LyricData data;
    private Long lineKey;
    private String current = "", next = "", outgoing = "";
    private long lastPlayback = -1, lastFrame, elapsed = TRANSITION_NANOS;
    private boolean initialized;

    public record Row(String text, float offsetRows, float opacity) {}
    public record Frame(String current, String next, String outgoing, float progress, boolean transitioning) {
        public static Frame empty() { return new Frame("", "", "", 1, false); }
        public static Frame preview() { return new Frame("正在播放的这一句歌词", "下一句会平滑地跟上来", "", 1, false); }
        public boolean hasText() { return !current.isBlank() || !next.isBlank() || !outgoing.isBlank(); }

         
        public List<Row> rows() {
            List<Row> rows = new ArrayList<>(2);
            if (!transitioning) {
                add(rows, current, 0, 1);
                add(rows, next, 1, .55F);
                return rows;
            }
            float p = Math.max(0, Math.min(1, progress));
            float move = 1 - (float)Math.pow(1 - p, 3);
            add(rows, outgoing, -move, 1 - smooth(Math.min(1, p * 2)));
            add(rows, current, 1 - move, .55F + .45F * move);
            add(rows, next, 1 + .2F * (1 - move), .55F * smooth(Math.max(0, p * 2 - 1)));
            return rows;
        }
        private static float smooth(float x) { return x * x * (3 - 2 * x); }
        private static void add(List<Row> rows, String text, float offset, float opacity) {
            if (!text.isBlank() && opacity > .001F) rows.add(new Row(text, offset, opacity));
        }
    }

    public Frame frame(String targetId, String songIdentity, LyricRepository.LyricData lyrics,
                       long playbackMillis, long nowNanos, boolean paused) {
        if (lyrics == null || lyrics.lines().isEmpty() || playbackMillis < 0) {
            reset();
            return Frame.empty();
        }
        boolean contextChanged = !initialized || !Objects.equals(target, targetId)
                || !Objects.equals(identity, songIdentity) || data != lyrics;
        boolean seek = initialized && (playbackMillis < lastPlayback
                || playbackMillis - lastPlayback > SEEK_DELTA_MILLIS);
        if (initialized && !paused) elapsed = Math.min(TRANSITION_NANOS,
                elapsed + Math.min(TRANSITION_NANOS, Math.max(0, nowNanos - lastFrame)));

        var entry = lyrics.lines().floorEntry(playbackMillis);
        Long key = entry == null ? null : entry.getKey();
        String selected = entry == null ? "" : safe(entry.getValue());
        var following = key == null ? lyrics.lines().firstEntry() : lyrics.lines().higherEntry(key);
        String followingText = following == null ? "" : safe(following.getValue());
        boolean changedLine = !Objects.equals(lineKey, key);
        if (contextChanged || seek) {
            elapsed = TRANSITION_NANOS;
            outgoing = "";
        } else if (changedLine) {
            Long expected = lineKey == null ? lyrics.lines().firstKey() : lyrics.lines().higherKey(lineKey);
             
            if (Objects.equals(expected, key) && !current.isBlank() && !selected.isBlank()
                    && elapsed >= TRANSITION_NANOS) {
                outgoing = current;
                elapsed = 0;
            } else {
                outgoing = "";
                elapsed = TRANSITION_NANOS;
            }
        }
        initialized = true;
        target = targetId; identity = songIdentity; data = lyrics; lineKey = key;
        current = selected; next = followingText;
        lastPlayback = playbackMillis; lastFrame = nowNanos;
        boolean transitioning = elapsed < TRANSITION_NANOS;
        if (!transitioning) outgoing = "";
        return new Frame(current, next, outgoing, elapsed / (float)TRANSITION_NANOS, transitioning);
    }

    public void reset() {
        target = identity = null; data = null; lineKey = null;
        current = next = outgoing = ""; lastPlayback = -1; lastFrame = 0;
        elapsed = TRANSITION_NANOS; initialized = false;
    }
    private static String safe(String text) { return text == null ? "" : text.replace('\n', ' ').replace('\r', ' '); }
}

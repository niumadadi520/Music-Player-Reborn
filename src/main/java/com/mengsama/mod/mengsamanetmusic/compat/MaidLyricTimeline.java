package com.mengsama.mod.mengsamanetmusic.compat;

import java.util.NavigableMap;

 
public final class MaidLyricTimeline {
    private long lastLineMs = Long.MIN_VALUE;
    private long cachedPlaybackMs = Long.MIN_VALUE;
    private java.util.Map.Entry<Long, String> cachedLine;
    private boolean stopped;
    private boolean wasPlaying;

    public String update(NavigableMap<Long, String> lines, long playbackMs, boolean playing) {
        if (stopped || lines == null || lines.isEmpty()) return null;
        if (!playing) { wasPlaying = false; return null; }
        boolean resumed = !wasPlaying;
        wasPlaying = true;
        long normalizedMs = Math.max(0L, playbackMs);
        var line = lines.floorEntry(normalizedMs);
        if (line == null) { lastLineMs = Long.MIN_VALUE; cachedLine = null; cachedPlaybackMs = normalizedMs; return ""; }
        cachedLine = line;
        cachedPlaybackMs = normalizedMs;
         
        if (!resumed && line.getKey() == lastLineMs) return null;
        lastLineMs = line.getKey();
        return line.getValue();
    }

    public void stop() { stopped = true; wasPlaying = false; lastLineMs = Long.MIN_VALUE; cachedLine = null; cachedPlaybackMs = Long.MIN_VALUE; }
    public void restart() { stopped = false; wasPlaying = false; lastLineMs = Long.MIN_VALUE; cachedLine = null; cachedPlaybackMs = Long.MIN_VALUE; }
}

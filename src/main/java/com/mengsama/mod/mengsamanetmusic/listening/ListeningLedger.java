package com.mengsama.mod.mengsamanetmusic.listening;

import java.util.*;

 
public final class ListeningLedger {
    public record Row(String key, String title, String detail, long value) {}
    private final Map<String,Row> songs = new HashMap<>(), players = new HashMap<>();
    private final Map<UUID,Long> lastHeard = new HashMap<>();
    private long revision;
    public long revision() { return revision; }
    public void played(String key, String title, String detail) {
        if (key == null || key.isBlank() || !songs.containsKey(key) && songs.size() >= 100000) return;
        Row old = songs.get(key);
        songs.put(key, new Row(key, clean(title,160),clean(detail,160), increment(old == null ? 0 : old.value,1)));
        revision++;
    }
    public void sample(UUID player, String name, long tick, boolean heard) {
        if (!heard) { lastHeard.remove(player); return; }
        Long before = lastHeard.put(player,tick);
        if (before == null || tick <= before || tick - before > 40) return;
        String key = player.toString();
        if (!players.containsKey(key) && players.size() >= 100000) return;
        Row old = players.get(key);
        players.put(key,new Row(key,clean(name,64),"",increment(old == null ? 0 : old.value,Math.min(20,tick-before))));
        revision++;
    }
    public void forget(UUID player) { lastHeard.remove(player); }
    public List<Row> ranked(boolean player) {
        return (player ? players : songs).values().stream().sorted(Comparator.comparingLong(Row::value).reversed()
                .thenComparing(Row::key)).toList();
    }
    public void restore(boolean player, Row row) {
        if (row.key == null || row.key.isBlank() || row.value < 0) return;
        Map<String,Row> target = player ? players : songs;
        if (target.size() < 100000) target.put(row.key,new Row(row.key,clean(row.title,160),clean(row.detail,160),row.value));
    }
    static long increment(long value, long by) { return value > Long.MAX_VALUE - by ? Long.MAX_VALUE : value + by; }
    public static String clean(String value, int limit) {
        String text = Objects.requireNonNullElse(value,"").replaceAll("[\\p{Cntrl}§]","");
        return text.substring(0,Math.min(limit,text.length()));
    }
}

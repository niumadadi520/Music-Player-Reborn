package com.mengsama.mod.mengsamanetmusic.api;

import java.util.ArrayList;
import java.util.List;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

 
public final class LrcParser {
    private static final Pattern TIMESTAMP = Pattern.compile("\\[(\\d{1,3}):(\\d{1,2})(?:[.:](\\d{1,3}))?]");
    private static final Pattern OFFSET = Pattern.compile("(?i)^\\s*\\[offset\\s*:\\s*([+-]?\\d+)\\s*]");
    private static final Pattern BRACKET_METADATA = Pattern.compile("(?i)^\\s*\\[(ar|al|ti|by|re|ve|length|kana|language)\\s*:.*]\\s*$");
    private static final Pattern CREDIT = Pattern.compile("(?iu)^\\s*(?:作词|填词|词|lyric(?:s)?(?:\\s+by)?|作曲|曲|composer|编曲|arranger|制作人|producer|混音|母带|录音|吉他|贝斯|鼓|和声|统筹|监制|出品|发行)\\s*[:：]\\s*\\S.*$");
    private static final Pattern WORD_TIMING = Pattern.compile("(?:<\\d{1,3}:\\d{1,2}(?:[.:]\\d{1,3})?>)|(?:\\(\\d+,\\d+(?:,\\d+)?\\))");

    private LrcParser() {}

    public static NavigableMap<Long, String> parseMillis(String lrc) {
        NavigableMap<Long, String> result = new TreeMap<>();
        if (lrc == null || lrc.isBlank()) return result;
        long offset = offset(lrc);
        List<ParsedLine> parsed = new ArrayList<>();
        for (String line : lrc.split("\\R")) {
            if (OFFSET.matcher(line).matches() || BRACKET_METADATA.matcher(line).matches()) continue;
            Matcher matcher = TIMESTAMP.matcher(line);
            List<Long> times = new ArrayList<>();
            int textStart = 0;
            while (matcher.find()) {
                long minutes = Long.parseLong(matcher.group(1));
                long seconds = Long.parseLong(matcher.group(2));
                String fraction = matcher.group(3);
                long millis = fraction == null ? 0 : fraction.length() == 1
                        ? Long.parseLong(fraction) * 100 : fraction.length() == 2
                        ? Long.parseLong(fraction) * 10 : Long.parseLong(fraction.substring(0, 3));
                times.add(Math.max(0, (minutes * 60 + seconds) * 1000 + millis + offset));
                textStart = matcher.end();
            }
            String text = clean(times.isEmpty() ? line : line.substring(textStart));
            if (!text.isEmpty()) parsed.add(new ParsedLine(times, text));
        }
         
         
        int firstLyric = -1;
        for (int i = 0; i < parsed.size(); i++) {
            ParsedLine line = parsed.get(i);
            if (!line.times.isEmpty() && !isMetadata(line.text)) { firstLyric = i; break; }
        }
        List<String> untimed = new ArrayList<>();
        for (int i = 0; i < parsed.size(); i++) {
            ParsedLine line = parsed.get(i);
            if (firstLyric >= 0 && i < firstLyric && isMetadata(line.text)) continue;
            if (line.times.isEmpty()) untimed.add(line.text);
            else for (long time : line.times) merge(result, time, line.text);
        }
         
        if (result.isEmpty()) for (int i = 0; i < untimed.size(); i++) merge(result, (long) i * 3000, untimed.get(i));
        return result;
    }

     
    public static NavigableMap<Integer, String> parse(String lrc) {
        NavigableMap<Integer, String> ticks = new TreeMap<>();
        parseMillis(lrc).forEach((ms, text) -> ticks.put((int) Math.min(Integer.MAX_VALUE, (ms + 25) / 50), text));
        return ticks;
    }

    public static String lineAt(NavigableMap<Long, String> lines, long playbackMs) {
        if (lines == null || lines.isEmpty()) return null;
        var entry = lines.floorEntry(Math.max(0, playbackMs));
        return entry == null ? null : entry.getValue();
    }

     
    public static NavigableMap<Long, String> mergeTranslation(NavigableMap<Long, String> original,
                                                               NavigableMap<Long, String> translated) {
        NavigableMap<Long, String> merged = new TreeMap<>();
        if (original == null) return merged;
        for (var entry : original.entrySet()) {
            String text = entry.getValue();
            if (translated != null && !translated.isEmpty()) {
                var floor = translated.floorEntry(entry.getKey());
                var ceil = translated.ceilingEntry(entry.getKey());
                var nearest = floor == null ? ceil : ceil == null ? floor
                        : entry.getKey() - floor.getKey() <= ceil.getKey() - entry.getKey() ? floor : ceil;
                if (nearest != null && Math.abs(nearest.getKey() - entry.getKey()) <= 750
                        && nearest.getValue() != null && !nearest.getValue().isBlank()
                        && !nearest.getValue().equals(text)) text += "\n" + nearest.getValue();
            }
            merged.put(entry.getKey(), text);
        }
        return merged;
    }

    private static long offset(String lrc) {
        for (String line : lrc.split("\\R")) {
            Matcher matcher = OFFSET.matcher(line);
            if (matcher.matches()) try { return Long.parseLong(matcher.group(1)); }
            catch (NumberFormatException ignored) { return 0; }
        }
        return 0;
    }

    private static String clean(String text) {
        return WORD_TIMING.matcher(text).replaceAll("").replaceAll("\\s+", " ").trim();
    }

    static boolean isMetadata(String text) {
        if (text == null || text.isBlank()) return true;
         
        return CREDIT.matcher(text).matches() || BRACKET_METADATA.matcher(text).matches();
    }

    private static void merge(NavigableMap<Long, String> lines, long time, String text) {
        lines.merge(time, text, (oldValue, newValue) -> oldValue.equals(newValue)
                ? oldValue : oldValue + " / " + newValue);
    }

    private record ParsedLine(List<Long> times, String text) {}
}

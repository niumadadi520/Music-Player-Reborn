package com.mengsama.mod.mengsamanetmusic.compat;

import com.google.gson.*;
import java.util.*;

 
public final class LegacyLyricArchive {
    public record Line(String primary, String translated) {}
    private record Entry(float second, String text) {}
    private final List<Entry> lines;
    private final Map<Float, String> translated;
    private final JsonObject original;
    public LegacyLyricArchive(Map<Float, String> primary, Map<Float, String> translated) {
        this(primary, translated, new JsonObject());
    }
    private LegacyLyricArchive(Map<Float, String> primary, Map<Float, String> translated, JsonObject original) {
        this.original = original.deepCopy();
        this.lines = cleaned(primary).entrySet().stream().sorted(Map.Entry.comparingByKey())
                .map(e -> new Entry(e.getKey(), e.getValue())).toList();
        this.translated = translated == null ? null : Map.copyOf(cleaned(translated));
    }
    private static Map<Float, String> cleaned(Map<Float, String> source) {
        Map<Float, String> result = new HashMap<>();
        if (source != null) source.forEach((time, text) -> {
            if (time != null && Float.isFinite(time) && time >= 0 && text != null) result.put(time, text);
        });
        return result;
    }
    public Line at(float second) {
        if (!Float.isFinite(second)) return new Line("", null);
        int low = 0, high = lines.size();
        while (low < high) {
            int middle = low + (high - low) / 2;
            if (lines.get(middle).second() <= second) low = middle + 1; else high = middle;
        }
        if (low == 0) return new Line("", null);
        Entry current = lines.get(low - 1);
        return new Line(current.text(), translated == null ? null : translated.get(current.second()));
    }
    public String toJson() {
        JsonObject result = original.deepCopy(), primary = new JsonObject();
        lines.forEach(e -> primary.addProperty(Float.toString(e.second()), e.text()));
        result.add("lyric", primary);
        if (translated != null) {
            JsonObject translation = new JsonObject();
            translated.forEach((time, text) -> translation.addProperty(Float.toString(time), text));
            result.add("transform_lyric", translation);
        }
        return result.toString();
    }
    public static LegacyLyricArchive fromJson(String json) {
        JsonElement value = JsonParser.parseString(json);
        JsonObject root = value.isJsonObject() ? value.getAsJsonObject() : new JsonObject();
        return new LegacyLyricArchive(rows(root.get("lyric")), root.has("transform_lyric") ? rows(root.get("transform_lyric")) : null, root);
    }
    private static Map<Float, String> rows(JsonElement value) {
        Map<Float, String> result = new HashMap<>();
        if (value != null && value.isJsonObject()) value.getAsJsonObject().entrySet().forEach(e -> {
            try { result.put(Float.parseFloat(e.getKey()), e.getValue().getAsString()); }
            catch (RuntimeException malformed) {   }
        });
        return result;
    }
}

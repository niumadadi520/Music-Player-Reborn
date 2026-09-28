package com.mengsama.mod.mengsamanetmusic.api.qq;

import com.google.gson.*;
import java.io.IOException;
import java.util.*;

 


public final class QqFields {

    public static JsonObject parse(String json) throws IOException {
        try {
            return JsonParser.parseString(json).getAsJsonObject();
        } catch (RuntimeException malformed) {
            throw new IOException("Invalid QQ JSON response", malformed);
        }
    }

    public static JsonObject object(JsonObject source, String... path) {
        JsonElement value = source;
        for (String key : path) {
            if (value == null || !value.isJsonObject())
                return new JsonObject();
            value = value.getAsJsonObject().get(key);
        }
        return value != null && value.isJsonObject() ? value.getAsJsonObject() : new JsonObject();
    }

    public static JsonArray rows(JsonObject source, String key) {
        JsonElement value = source.get(key);
        return value != null && value.isJsonArray() ? value.getAsJsonArray() : new JsonArray();
    }

    public static String text(JsonObject source, String... alternatives) {
        for (String key : alternatives) {
            JsonElement value = source.get(key);
            if (value != null && value.isJsonPrimitive()) {
                String text = value.getAsString().strip();
                if (!text.isEmpty())
                    return text;
            }
        }
        return "";
    }

    public static long number(JsonObject source, String key, long fallback) {
        try {
            return source.has(key) ? source.get(key).getAsLong() : fallback;
        } catch (RuntimeException invalid) {
            return fallback;
        }
    }

    public static JsonObject data(JsonObject root, String key) throws IOException {
        JsonObject result = object(root, key);
        if (number(root, "code", 0) != 0 || result.size() == 0 || number(result, "code", 0) != 0)
            throw new IOException("QQ service rejected " + key);
        JsonObject data = object(result, "data");
        if (data.size() == 0)
            throw new IOException("QQ service omitted data for " + key);
        return data;
    }

    public static JsonObject properties(Object... pairs) {
        JsonObject object = new JsonObject();
        for (int i = 0; i < pairs.length; i += 2) {
            String key = (String) pairs[i];
            Object value = pairs[i + 1];
            if (value instanceof JsonElement element)
                object.add(key, element);
            else if (value instanceof Number number)
                object.addProperty(key, number);
            else if (value instanceof Boolean flag)
                object.addProperty(key, flag);
            else
                object.addProperty(key, String.valueOf(value));
        }
        return object;
    }

    public static JsonArray array(Object... values) {
        JsonArray rows = new JsonArray();
        for (Object value : values) {
            if (value instanceof Number number)
                rows.add(number);
            else
                rows.add(String.valueOf(value));
        }
        return rows;
    }
}

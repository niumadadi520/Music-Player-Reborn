package com.mengsama.mod.mengsamanetmusic.api;

import com.google.gson.JsonObject;

public final class LyricPayload {

    private LyricPayload() {
    }

    public static String text(JsonObject root, String objectKey, String... aliases) {
        if (root == null)
            return "";
        if (root.has(objectKey)) {
            var value = root.get(objectKey);
            if (value.isJsonPrimitive())
                return value.getAsString();
            if (value.isJsonObject())
                for (String alias : aliases) {
                    if (value.getAsJsonObject().has(alias) && value.getAsJsonObject().get(alias).isJsonPrimitive())
                        return value.getAsJsonObject().get(alias).getAsString();
                }
        }
        for (String alias : aliases) if (root.has(alias) && root.get(alias).isJsonPrimitive())
            return root.get(alias).getAsString();
        return "";
    }
}

package com.mengsama.mod.mengsamanetmusic.api;

import java.util.Objects;

 
public final class ParsedUrl {
    public enum ResourceType {
        ALBUM, SONG, PLAYLIST
    }

    private final ResourceType category;
    private final String resourceKey;

    public ParsedUrl(ResourceType category, String resourceKey) {
        this.category = Objects.requireNonNull(category, "category");
        String normalized = Objects.requireNonNullElse(resourceKey, "").trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("resourceKey must not be blank");
        }
        this.resourceKey = normalized;
    }

    public ResourceType getType() {
        return category;
    }

    public String getId() {
        return resourceKey;
    }

    @Override
    public String toString() {
        return category.name().toLowerCase() + ":" + resourceKey;
    }
}

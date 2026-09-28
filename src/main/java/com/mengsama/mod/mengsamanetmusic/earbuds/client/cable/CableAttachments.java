package com.mengsama.mod.mengsamanetmusic.earbuds.client.cable;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

 
public final class CableAttachments {
    public enum Kind { HAND, BACKPACK }
    public record Plug(Kind kind, CableCurve.Point point) {}
    private final Map<UUID, EnumMap<Kind, CableCurve.Point>> plugs = new HashMap<>();
    public void clear() { plugs.clear(); }
    public void capture(UUID device, Kind kind, CableCurve.Point point) {
        if (device != null) plugs.computeIfAbsent(device, ignored -> new EnumMap<>(Kind.class)).put(kind, point);
    }
    public Plug resolve(UUID device) {
        var samples = plugs.get(device);
        if (samples != null) for (Kind kind : Kind.values()) {
            var point = samples.get(kind);
            if (point != null) return new Plug(kind, point);
        }
        return null;
    }
    public static boolean visible(boolean firstPerson, UUID camera, UUID owner, UUID guest) {
        return !firstPerson || camera == null || (!camera.equals(owner) && !camera.equals(guest));
    }
}

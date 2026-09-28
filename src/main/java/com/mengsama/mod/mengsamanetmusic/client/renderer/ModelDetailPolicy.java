package com.mengsama.mod.mengsamanetmusic.client.renderer;

 
public final class ModelDetailPolicy {
    public enum Detail { FULL, MEDIUM, FAR }
    private ModelDetailPolicy() {}

    public static Detail select(double distanceSquared, Detail previous) {
        if (!Double.isFinite(distanceSquared) || distanceSquared < 0) return Detail.FULL;
        if (previous == null) return distanceSquared <= 144 ? Detail.FULL
                : distanceSquared <= 576 ? Detail.MEDIUM : Detail.FAR;
        if (previous == Detail.FULL && distanceSquared <= 144) return Detail.FULL;
        if (distanceSquared <= 100) return Detail.FULL;
        if (previous == Detail.FAR && distanceSquared > 484) return Detail.FAR;
        return distanceSquared <= 576 ? Detail.MEDIUM : Detail.FAR;
    }
}

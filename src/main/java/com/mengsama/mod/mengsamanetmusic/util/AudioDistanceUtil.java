package com.mengsama.mod.mengsamanetmusic.util;

 
final class AudioDistanceUtil {
    private AudioDistanceUtil() {}

    static float linearVolume(double distance, double maxDistance) {
        if (maxDistance <= 0.0 || distance >= maxDistance) return 0f;
        if (distance <= 0.0) return 1f;
        return (float) (1.0 - distance / maxDistance);
    }

    static float smoothVolume(float current, float target, float factor) {
        if (target <= 0f) return 0f;
        float clampedFactor = Math.max(0f, Math.min(1f, factor));
        return current + (target - current) * clampedFactor;
    }
}

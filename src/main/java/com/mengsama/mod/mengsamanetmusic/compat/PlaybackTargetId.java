package com.mengsama.mod.mengsamanetmusic.compat;

import java.util.UUID;

 
public final class PlaybackTargetId {
    private PlaybackTargetId() {}

    public static UUID instanceId(String targetId) {
        if (targetId == null) return null;
        int separator = targetId.lastIndexOf(':');
        if (separator < 0 || separator == targetId.length() - 1) return null;
        try {
            return UUID.fromString(targetId.substring(separator + 1));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    public static boolean isMaidTarget(String targetId) {
        return targetId != null && targetId.startsWith("maid:");
    }

     
    public static boolean isItemOwner(String targetId, UUID playerId) {
        return playerId != null && targetId != null && instanceId(targetId) != null
                && targetId.startsWith("item:" + playerId + ":");
    }

    public static boolean maidMatches(String targetId, UUID maidId, UUID instanceId) {
        if (!isMaidTarget(targetId) || maidId == null || instanceId == null) return false;
        return targetId.endsWith(":" + maidId + ":" + instanceId);
    }
}

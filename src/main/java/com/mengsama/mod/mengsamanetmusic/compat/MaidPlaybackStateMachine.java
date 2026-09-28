package com.mengsama.mod.mengsamanetmusic.compat;

 



public final class MaidPlaybackStateMachine {
    public static final int SYNC_GRACE_TICKS = 40;

    private MaidPlaybackStateMachine() {}

    public enum Decision { VALID, WAIT_FOR_SYNC, STOP }

    public static Decision evaluate(boolean entityAlive, boolean entityIdentityMatches,
                                    boolean taskMatches, boolean devicePresent,
                                    boolean instanceMatches, boolean bindingConfirmed,
                                    int unresolvedTicks) {
        if (!entityAlive || !entityIdentityMatches) return Decision.STOP;
        if (taskMatches && devicePresent && instanceMatches) return Decision.VALID;
        if (bindingConfirmed) return Decision.STOP;
        return unresolvedTicks < SYNC_GRACE_TICKS ? Decision.WAIT_FOR_SYNC : Decision.STOP;
    }

    public static boolean targetMatches(String targetId, java.util.UUID maidId, java.util.UUID instanceId) {
        return PlaybackTargetId.maidMatches(targetId, maidId, instanceId);
    }
}

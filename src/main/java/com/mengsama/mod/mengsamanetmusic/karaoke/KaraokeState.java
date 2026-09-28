package com.mengsama.mod.mengsamanetmusic.karaoke;

import java.util.List;
import java.util.UUID;

 
public record KaraokeState(int containerId, Kind kind, UUID microphoneId, boolean enabled,
                           boolean mine, boolean voiceAvailable, int volume,
                           List<String> connections, String message) {
    public enum Kind { NONE, HANDHELD, STANDING, SPEAKER }
    public KaraokeState { connections = List.copyOf(connections); }
}

package com.mengsama.mod.mengsamanetmusic.client.audio;

import com.mengsama.mod.mengsamanetmusic.mixin.SoundEngineAccessorMixin;
import com.mengsama.mod.mengsamanetmusic.mixin.SoundManagerAccessorMixin;
import com.mengsama.mod.mengsamanetmusic.util.NetMusicSound;
import com.mengsama.mod.mengsamanetmusic.util.PlayerNetMusicSound;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

 
@OnlyIn(Dist.CLIENT)
public final class ClientMusicPlayback {
    private static final AtomicLong LOCAL_SEQUENCE = new AtomicLong();
    private static final Map<String, AtomicLong> GENERATIONS = new ConcurrentHashMap<>();
    private static final Map<String, AtomicLong> SERVER_GENERATIONS = new ConcurrentHashMap<>();
    private static final Map<String, Object> ACTIVE_SOUNDS = new ConcurrentHashMap<>();
    private static final Map<String, Boolean> PAUSED = new ConcurrentHashMap<>();
    private static final Map<String, Long> PAUSE_GENERATIONS = new ConcurrentHashMap<>();
    private static final Map<String, Long> PAUSE_SEQUENCES = new ConcurrentHashMap<>();
    private static final Map<String, RefreshAttempt> REFRESH_ATTEMPTS = new ConcurrentHashMap<>();
    private static final Map<String, Long> REFRESH_CHAINS = new ConcurrentHashMap<>();
    private static final Map<String, PendingSeek> PENDING_SEEKS = new ConcurrentHashMap<>();
    private static final AtomicLong NONCES = new AtomicLong(System.nanoTime());
    private static final long SEEK_RESPONSE_TIMEOUT_NANOS = 10_000_000_000L;

    private record RefreshAttempt(long failedGeneration, long nonce) {}
    private record PendingSeek(int second, String identity, long createdNanos) {}

    private ClientMusicPlayback() {
    }

    public static void stopPhysicalDevice(java.util.UUID instance) {
        if (instance == null) return;
        for (String target : java.util.List.copyOf(ACTIVE_SOUNDS.keySet())) {
            if (instance.equals(com.mengsama.mod.mengsamanetmusic.compat.PlaybackTargetId.instanceId(target))) {
                stop(target);
                com.mengsama.mod.mengsamanetmusic.earbuds.client.EarbudClient.forget(target);
                com.mengsama.mod.mengsamanetmusic.hud.MusicInfoHud.onDeviceStopped(target);
            }
        }
    }

    private static String generationKey(String targetId) {
        var instance = com.mengsama.mod.mengsamanetmusic.compat.PlaybackTargetId.instanceId(targetId);
        return instance == null ? targetId : "device:" + instance;
    }

    public static long beginSwitch(String targetId) {
         
        String physical = generationKey(targetId);
        var previousTargets = new java.util.HashSet<>(GENERATIONS.keySet());
        previousTargets.addAll(ACTIVE_SOUNDS.keySet());
        for (String previous : previousTargets) {
            if (!previous.equals(targetId) && generationKey(previous).equals(physical)) {
                stop(previous);
                PENDING_SEEKS.remove(previous);
                REFRESH_ATTEMPTS.remove(previous);
            }
        }
        long generation = GENERATIONS.computeIfAbsent(targetId, ignored -> new AtomicLong()).updateAndGet(ignored -> LOCAL_SEQUENCE.incrementAndGet());
        long serverGeneration = serverGeneration(targetId);
        Long pauseGeneration = PAUSE_GENERATIONS.get(targetId);
         
         
        if (pauseGeneration == null || pauseGeneration != serverGeneration) {
            PAUSED.remove(targetId);
            PAUSE_GENERATIONS.remove(targetId);
            PAUSE_SEQUENCES.remove(targetId);
        }
        stop(ACTIVE_SOUNDS.remove(targetId));
        return generation;
    }

     
    public static boolean acceptServerGeneration(String targetId, long generation) {
        return acceptServerGeneration(targetId, generation, 0L);
    }

     
    public static boolean acceptServerGeneration(String targetId, long generation, long refreshNonce) {
        if (refreshNonce != 0L) {
            RefreshAttempt pending = REFRESH_ATTEMPTS.get(targetId);
            if (pending == null || pending.nonce != refreshNonce || generation <= pending.failedGeneration) return false;
        }
        AtomicLong current = SERVER_GENERATIONS.computeIfAbsent(generationKey(targetId), ignored -> new AtomicLong(Long.MIN_VALUE));
        while (true) {
            long seen = current.get();
            if (generation <= seen) return false;
            if (current.compareAndSet(seen, generation)) {
                if (refreshNonce != 0L) {
                    REFRESH_ATTEMPTS.remove(targetId);
                    REFRESH_CHAINS.put(targetId, generation);
                } else {
                    REFRESH_CHAINS.remove(targetId);
                }
                return true;
            }
        }
    }

     
    public static long beginRefresh(String targetId, long failedGeneration) {
        if (targetId == null || targetId.isBlank() || failedGeneration == Long.MIN_VALUE
                || REFRESH_CHAINS.containsKey(targetId)) {
            return 0L;
        }
        final long[] issued = {0L};
        REFRESH_ATTEMPTS.compute(targetId, (ignored, previous) -> {
            if (previous != null && previous.failedGeneration == failedGeneration) return previous;
            long nonce = NONCES.incrementAndGet();
            issued[0] = nonce;
            return new RefreshAttempt(failedGeneration, nonce);
        });
        return issued[0];
    }

    public static void resetSession() {
        for (Object sound : java.util.List.copyOf(ACTIVE_SOUNDS.values())) stop(sound);
        GENERATIONS.clear();
        SERVER_GENERATIONS.clear();
        ACTIVE_SOUNDS.clear();
        PAUSED.clear();
        PAUSE_GENERATIONS.clear();
        PAUSE_SEQUENCES.clear();
        REFRESH_ATTEMPTS.clear();
        REFRESH_CHAINS.clear();
        PENDING_SEEKS.clear();
    }

    static void resetForTest() { resetSession(); }

    public static long serverGeneration(String targetId) {
        AtomicLong current = SERVER_GENERATIONS.get(generationKey(targetId));
        return current == null ? Long.MIN_VALUE : current.get();
    }

    public static void register(String targetId, Object sound) {
        Object previous = ACTIVE_SOUNDS.put(targetId, sound);
        if (previous != sound) stop(previous);
    }

     
    public static boolean seekImmediately(String targetId, int second) {
        Object active = ACTIVE_SOUNDS.get(targetId);
        if (active == null) return false;
        boolean paused = isPaused(targetId);
        long generation = beginSwitch(targetId);
        try {
            if (active instanceof NetMusicSound sound) {
                int start = Math.max(0, Math.min(sound.getDurationSeconds(), second));
                PENDING_SEEKS.put(targetId, new PendingSeek(start, sound.getSongInfo().identityKey(), System.nanoTime()));
                NetMusicSound replacement = new NetMusicSound(sound.getPos(), sound.getUrl(),
                        sound.getDurationSeconds(), targetId, generation, sound.getSongInfo(), start);
                register(targetId, replacement);
                Minecraft.getInstance().getSoundManager().play(replacement);
                setPaused(targetId, paused);
                return true;
            }
            if (active instanceof PlayerNetMusicSound sound) {
                int start = Math.max(0, Math.min(sound.getDurationSeconds(), second));
                PENDING_SEEKS.put(targetId, new PendingSeek(start, sound.getSongInfo().identityKey(), System.nanoTime()));
                PlayerNetMusicSound replacement = new PlayerNetMusicSound(sound.getPlayer(), sound.getUrl(),
                        sound.getDurationSeconds(), sound.getSlot(), targetId, generation, sound.getSongInfo(), start);
                register(targetId, replacement);
                Minecraft.getInstance().getSoundManager().play(replacement);
                setPaused(targetId, paused);
                return true;
            }
        } catch (RuntimeException ignored) {
            PENDING_SEEKS.remove(targetId);
            stop(targetId);
        }
        return false;
    }

     



    public static boolean consumeSeekResponse(String targetId, int second, String songIdentity) {
        PendingSeek pending = PENDING_SEEKS.get(targetId);
        if (pending == null) return false;
        if (System.nanoTime() - pending.createdNanos > SEEK_RESPONSE_TIMEOUT_NANOS) {
            PENDING_SEEKS.remove(targetId, pending);
            return false;
        }
        String identity = songIdentity == null ? "" : songIdentity;
        boolean sameIdentity = pending.identity.isBlank() || identity.isBlank() || pending.identity.equals(identity);
        if (!sameIdentity) {
            PENDING_SEEKS.remove(targetId, pending);
            return false;
        }
        if (pending.second != second) return true;
        PENDING_SEEKS.remove(targetId, pending);
        return true;
    }

     
    public static boolean unregister(String targetId, Object sound) {
        boolean removed = ACTIVE_SOUNDS.remove(targetId, sound);
        if (removed) PAUSED.remove(targetId);
        return removed;
    }

    public static boolean isCurrent(String targetId, long generation) {
        AtomicLong current = GENERATIONS.get(targetId);
        return current != null && current.get() == generation;
    }

    static boolean isCurrentSound(String targetId, long generation, Object sound) {
        return isCurrent(targetId, generation) && ACTIVE_SOUNDS.get(targetId) == sound;
    }

    public static void stop(String targetId) {
        GENERATIONS.computeIfAbsent(targetId, ignored -> new AtomicLong()).updateAndGet(ignored -> LOCAL_SEQUENCE.incrementAndGet());
        PAUSED.remove(targetId);
        PAUSE_GENERATIONS.remove(targetId);
        PAUSE_SEQUENCES.remove(targetId);
        stop(ACTIVE_SOUNDS.remove(targetId));
    }

     
    public static boolean setPaused(String targetId, boolean paused) {
        return setPaused(targetId, paused, Long.MIN_VALUE);
    }

    public static boolean setPaused(String targetId, boolean paused, long requestGeneration) {
        return setPaused(targetId, paused, requestGeneration, Long.MIN_VALUE);
    }

    public static boolean setPaused(String targetId, boolean paused, long requestGeneration, long controlSequence) {
        if (!acceptPauseCommand(targetId, requestGeneration, controlSequence)) return false;
        if (paused) PAUSED.put(targetId, true); else PAUSED.remove(targetId);
        Object active = ACTIVE_SOUNDS.get(targetId);
        if (active instanceof SoundInstance sound) applyChannelState(sound, paused);
        return active != null || paused;
    }

    static boolean acceptPauseCommand(String targetId, long requestGeneration, long controlSequence) {
        long serverGeneration = serverGeneration(targetId);
        if (requestGeneration != Long.MIN_VALUE && serverGeneration != Long.MIN_VALUE
                && requestGeneration < serverGeneration) return false;
        if (controlSequence != Long.MIN_VALUE) {
            final boolean[] accepted = {false};
            PAUSE_SEQUENCES.compute(targetId, (ignored, previous) -> {
                if (previous == null || controlSequence > previous) {
                    accepted[0] = true;
                    return controlSequence;
                }
                return previous;
            });
            if (!accepted[0]) return false;
        }
        if (requestGeneration != Long.MIN_VALUE) PAUSE_GENERATIONS.put(targetId, requestGeneration);
        return true;
    }

     
    public static void onChannelStarted(SoundInstance sound, com.mojang.blaze3d.audio.Channel channel) {
        if (sound instanceof StreamingDeviceSound stream) {
             
            if (!stream.mayStartChannel()) { channel.stop(); return; }
            stream.markChannelStarted();
        }
        String target = targetFor(sound);
        if (target != null && isPaused(target)) channel.pause();
    }

     
    public static void reapplyDevicePauses() {
        PAUSED.forEach((targetId, paused) -> {
            if (!Boolean.TRUE.equals(paused)) return;
            Object active = ACTIVE_SOUNDS.get(targetId);
            if (active instanceof SoundInstance sound) applyChannelState(sound, true);
        });
    }

    private static String targetFor(Object sound) {
        for (Map.Entry<String, Object> entry : ACTIVE_SOUNDS.entrySet()) {
            if (entry.getValue() == sound) return entry.getKey();
        }
        return null;
    }

    private static void applyChannelState(SoundInstance sound, boolean paused) {
        try {
            var manager = (SoundManagerAccessorMixin) Minecraft.getInstance().getSoundManager();
            var engine = (SoundEngineAccessorMixin) manager.getSoundEngine();
            var handle = engine.getInstanceToChannel().get(sound);
            if (handle != null && !handle.isStopped()) {
                handle.execute(paused ? channel -> channel.pause() : channel -> channel.unpause());
            }
        } catch (RuntimeException ignored) {
        }
    }

    public static boolean isPaused(String targetId) {
        return Boolean.TRUE.equals(PAUSED.get(targetId));
    }

    public static int getTick(String targetId) {
        Object sound = ACTIVE_SOUNDS.get(targetId);
        if (sound instanceof NetMusicSound blockSound) return blockSound.getTick();
        if (sound instanceof PlayerNetMusicSound playerSound) return playerSound.getTick();
        return -1;
    }

     
    public static com.mengsama.mod.mengsamanetmusic.api.SongInfo getSongInfo(String targetId) {
        Object sound = ACTIVE_SOUNDS.get(targetId);
        if (sound instanceof NetMusicSound blockSound) return blockSound.getSongInfo();
        if (sound instanceof PlayerNetMusicSound playerSound) return playerSound.getSongInfo();
        return null;
    }

    public static boolean isActive(String targetId) {
        return targetId != null && ACTIVE_SOUNDS.containsKey(targetId);
    }

     
    public static String authoritativeTarget(String requestedTarget) {
        if (requestedTarget == null || ACTIVE_SOUNDS.containsKey(requestedTarget)) return requestedTarget;
        java.util.UUID instanceId = com.mengsama.mod.mengsamanetmusic.compat.PlaybackTargetId.instanceId(requestedTarget);
        if (instanceId == null) return requestedTarget;
        String suffix = ":" + instanceId;
        for (String activeTarget : ACTIVE_SOUNDS.keySet()) {
            if (activeTarget.endsWith(suffix)) return activeTarget;
        }
        return requestedTarget;
    }

    public static java.util.List<com.mengsama.mod.mengsamanetmusic.listening.ListeningNetwork.Sample> listeningSources() {
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.isPaused() || mc.options.getSoundSourceVolume(net.minecraft.sounds.SoundSource.MASTER) <= 0
                || mc.options.getSoundSourceVolume(net.minecraft.sounds.SoundSource.RECORDS) <= 0) return java.util.List.of();
        java.util.List<com.mengsama.mod.mengsamanetmusic.listening.ListeningNetwork.Sample> samples = new java.util.ArrayList<>();
        for (var entry : ACTIVE_SOUNDS.entrySet()) {
            if (entry.getValue() instanceof StreamingDeviceSound sound && sound.listeningReady() && !isPaused(entry.getKey())
                    && sound.getVolume() > 0 && mc.getSoundManager().isActive(sound)) {
                samples.add(new com.mengsama.mod.mengsamanetmusic.listening.ListeningNetwork.Sample(entry.getKey(), serverGeneration(entry.getKey())));
                if (samples.size() == 32) break;
            }
        }
        return java.util.List.copyOf(samples);
    }

    private static void stop(Object sound) {
        if (sound instanceof NetMusicSound blockSound) {
            blockSound.stopSound();
        } else if (sound instanceof PlayerNetMusicSound playerSound) {
            playerSound.stopMusic();
        }
    }
}

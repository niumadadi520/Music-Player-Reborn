package com.mengsama.mod.mengsamanetmusic.util;

import com.mengsama.mod.mengsamanetmusic.api.SongInfo;
import com.mengsama.mod.mengsamanetmusic.client.audio.NetMusicAudioStream;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.net.URL;

public class PlayerNetMusicSound extends com.mengsama.mod.mengsamanetmusic.client.audio.StreamingDeviceSound {
    private static final long FAILURE_MESSAGE_INTERVAL_MS = 10_000L;
    private static final java.util.concurrent.ConcurrentHashMap<String, Long> LAST_FAILURE_MESSAGES =
            new java.util.concurrent.ConcurrentHashMap<>();
    static final double MAX_AUDIBLE_DISTANCE = 96.0;
    static final float VOLUME_SMOOTHING = 0.2f;

    final LivingEntity player;
    final int slot;
    int maidBindingUnresolvedTicks;
    boolean maidBindingConfirmed;

    public PlayerNetMusicSound(LivingEntity player, URL songUrl, int second, int slot, String targetId,
                               long playbackGeneration, SongInfo songInfo) {
        this(player, songUrl, second, slot, targetId, playbackGeneration, songInfo, 0);
    }

    public PlayerNetMusicSound(LivingEntity player, URL songUrl, int second, int slot, String targetId,
                               long playbackGeneration, SongInfo songInfo, int startSecond) {
        super(songUrl, second, targetId, playbackGeneration, songInfo, startSecond);
        this.player = player;
        relocate(player.position());
         
        Player listener = Minecraft.getInstance().player;
        this.volume = initialVolume(listener == null ? Double.POSITIVE_INFINITY
                : Math.sqrt(listener.distanceToSqr(this.x, this.y, this.z)));
        this.slot = slot;

    }

    static float initialVolume(double distance) {
        return AudioDistanceUtil.linearVolume(distance, MAX_AUDIBLE_DISTANCE);
    }

     





    public float getInitialVolume() {
        return this.volume;
    }

    @Override public float getVolume() {
        return super.getVolume() * com.mengsama.mod.mengsamanetmusic.client.audio.ClientMusicVolume.gain();
    }

    @Override public boolean canStartSilent() {
        return com.mengsama.mod.mengsamanetmusic.client.audio.ClientMusicVolume.percent() == 0 && this.volume > 0;
    }

    private boolean isValidTarget() {
        if (player.isRemoved() || !player.isAlive()) return false;
        if (targetId.startsWith("maid:")) {
            if (!com.mengsama.mod.mengsamanetmusic.compat.MaidMusicAccess.isMaid(player)) return false;
            LivingEntity maid = player;
            boolean taskMatches = com.mengsama.mod.mengsamanetmusic.compat.MaidMusicAccess.hasMusicTask(maid);
            ItemStack device = com.mengsama.mod.mengsamanetmusic.compat.EntityMusicDevice.heldPlayer(maid);
            java.util.UUID instanceId = device.isEmpty() ? null
                    : com.mengsama.mod.mengsamanetmusic.item.MusicPlayerItem.getInstanceId(device);
            boolean instanceMatches = com.mengsama.mod.mengsamanetmusic.compat.MaidPlaybackStateMachine
                    .targetMatches(targetId, maid.getUUID(), instanceId);
            var decision = com.mengsama.mod.mengsamanetmusic.compat.MaidPlaybackStateMachine.evaluate(
                    maid.isAlive() && !maid.isRemoved(), player.getUUID().equals(maid.getUUID()),
                    taskMatches, !device.isEmpty(), instanceMatches, maidBindingConfirmed,
                    maidBindingUnresolvedTicks);
            if (decision == com.mengsama.mod.mengsamanetmusic.compat.MaidPlaybackStateMachine.Decision.VALID) {
                maidBindingConfirmed = true;
                return true;
            }
            if (decision == com.mengsama.mod.mengsamanetmusic.compat.MaidPlaybackStateMachine.Decision.WAIT_FOR_SYNC) {
                maidBindingUnresolvedTicks++;
                return true;
            }
            return false;
        }
        return true;
    }

    private ItemStack findBoundPlayerDevice(Player localPlayer) {
        java.util.UUID expected = com.mengsama.mod.mengsamanetmusic.compat.PlaybackTargetId.instanceId(targetId);
        if (expected != null) {
            for (int i = 0; i < localPlayer.getInventory().getContainerSize(); i++) {
                ItemStack candidate = localPlayer.getInventory().getItem(i);
                if (candidate.getItem() instanceof com.mengsama.mod.mengsamanetmusic.item.MusicPlayerItem
                        && expected.equals(com.mengsama.mod.mengsamanetmusic.item.MusicPlayerItem.getInstanceId(candidate))) {
                    return candidate;
                }
            }
            ItemStack carried = localPlayer.containerMenu.getCarried();
            if (expected.equals(com.mengsama.mod.mengsamanetmusic.item.MusicPlayerItem.getInstanceId(carried))) return carried;
            return com.mengsama.mod.mengsamanetmusic.compat.PortableDevices.find(localPlayer, expected, true);
        }
        return com.mengsama.mod.mengsamanetmusic.item.MusicPlayerItem.findMusicPlayerItem(localPlayer);
    }

    public void onlyTickUpdate() {
        tick++;
    }

    static boolean acquireFailureMessagePermit(String targetId, NetMusicAudioStream.FailureCategory category, long nowMs) {
        String key = (targetId == null ? "" : targetId) + '|' + category;
        final boolean[] allowed = {false};
        LAST_FAILURE_MESSAGES.compute(key, (ignored, previous) -> {
            if (previous == null || nowMs - previous >= FAILURE_MESSAGE_INTERVAL_MS) {
                allowed[0] = true;
                return nowMs;
            }
            return previous;
        });
        return allowed[0];
    }

    static boolean acquireFailureMessagePermit(long nowMs) {
        return acquireFailureMessagePermit("test", NetMusicAudioStream.FailureCategory.RESOLUTION_FAILED, nowMs);
    }

    static void resetFailureMessageThrottleForTest() {
        LAST_FAILURE_MESSAGES.clear();
    }

    public LivingEntity getPlayer() {
        return player;
    }

    public int getSlot() {
        return slot;
    }

    public void stopMusic() { release(); }
    public boolean isClientPlayer() { return player.equals(Minecraft.getInstance().player); }
    @Override protected int endGraceTicks() { return 200; }
    @Override protected boolean emitsNotes() { return !com.mengsama.mod.mengsamanetmusic.earbuds.client.EarbudClient.isPrivate(targetId); }
    @Override protected double noteHeight() { return 1.5; }
    @Override protected boolean updateEmitter() {
        if (!isValidTarget()) return false;
         
        if (isClientPlayer() && player instanceof Player local
                && com.mengsama.mod.mengsamanetmusic.compat.PlaybackTargetId.isItemOwner(targetId, local.getUUID())
                && findBoundPlayerDevice(local).isEmpty()) return false;
        relocate(player.position());
        var listener = Minecraft.getInstance().player;
        float desired = initialVolume(listener == null ? Double.POSITIVE_INFINITY : Math.sqrt(listener.distanceToSqr(x, y, z)));
        volume = AudioDistanceUtil.smoothVolume(volume, desired, VOLUME_SMOOTHING);
        return true;
    }
    @Override protected com.mengsama.mod.mengsamanetmusic.network.RefreshPlaybackPacket refreshPacket(long generation, long nonce) {
        int source = com.mengsama.mod.mengsamanetmusic.earbuds.client.EarbudClient.sourceEntity(targetId, player.getId());
        return new com.mengsama.mod.mengsamanetmusic.network.RefreshPlaybackPacket(source, targetId, generation, nonce, songInfo);
    }
    @Override protected void reportFailure(Throwable failure) {
        var listener = Minecraft.getInstance().player;
        var category = NetMusicAudioStream.classifyFailure(failure);
        if (listener != null && acquireFailureMessagePermit(targetId, category, System.currentTimeMillis()))
            listener.displayClientMessage(net.minecraft.network.chat.Component.literal(NetMusicAudioStream.userFailureMessage(category)), false);
    }
}

package com.mengsama.mod.mengsamanetmusic.util;

import com.mengsama.mod.mengsamanetmusic.api.SongInfo;
import com.mengsama.mod.mengsamanetmusic.block.IMusicPlayerBlockEntity;
import com.mengsama.mod.mengsamanetmusic.client.audio.NetMusicAudioStream;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import java.net.URL;

public class NetMusicSound extends com.mengsama.mod.mengsamanetmusic.client.audio.StreamingDeviceSound {

    final BlockPos pos;

    public NetMusicSound(BlockPos pos, URL songUrl, int second, String targetId,
                         long playbackGeneration, SongInfo songInfo) {
        this(pos, songUrl, second, targetId, playbackGeneration, songInfo, 0);
    }

    public NetMusicSound(BlockPos pos, URL songUrl, int second, String targetId,
                         long playbackGeneration, SongInfo songInfo, int startSecond) {
        super(songUrl, second, targetId, playbackGeneration, songInfo, startSecond);
        this.pos = pos.immutable();
        relocate(net.minecraft.world.phys.Vec3.atCenterOf(pos));
        this.volume = 1.0F;

    }

    private void updateDistanceVolume() {
        var listener = Minecraft.getInstance().player;
        if (listener == null) { this.volume = 0.0F; return; }
        double distance = Math.sqrt(listener.distanceToSqr(x, y, z));
        double t = Math.max(0.0, Math.min(1.0, (distance - 4.0) / 44.0));
        this.volume = (float) (1.0 - t * t * (3.0 - 2.0 * t));
        var level = Minecraft.getInstance().level;
        var device = level == null ? null : level.getBlockEntity(pos);
        deviceGain = device instanceof com.mengsama.mod.mengsamanetmusic.karaoke.KaraokeBlockEntity speaker
                && speaker.isSpeaker() ? speaker.volume() / 100F : 1F;
    }

    private volatile float deviceGain = 1F;
    @Override public float getVolume() {
        return super.getVolume() * deviceGain * com.mengsama.mod.mengsamanetmusic.client.audio.ClientMusicVolume.gain();
    }

    @Override public boolean canStartSilent() {
         
        return (com.mengsama.mod.mengsamanetmusic.client.audio.ClientMusicVolume.percent() == 0 || deviceGain == 0) && this.volume > 0;
    }

    public BlockPos getPos() {
        return pos;
    }

    public void stopSound() { release(); }
    @Override protected int endGraceTicks() { return 50; }
    @Override protected boolean updateEmitter() {
        var world = Minecraft.getInstance().level;
        if (world == null || !world.hasChunkAt(pos)) return false;
        boolean present = world.getBlockEntity(pos) instanceof IMusicPlayerBlockEntity
                || com.mengsama.mod.mengsamanetmusic.compat.BackpackAccess.validBlock(world, pos, targetId);
        if (present) updateDistanceVolume();
        return present;
    }
    @Override protected com.mengsama.mod.mengsamanetmusic.network.RefreshPlaybackPacket refreshPacket(long generation, long nonce) {
        return new com.mengsama.mod.mengsamanetmusic.network.RefreshPlaybackPacket(pos, targetId, generation, nonce, songInfo);
    }
    @Override protected void reportFailure(Throwable failure) {
        Minecraft.getInstance().gui.setOverlayMessage(Component.literal(NetMusicAudioStream.userFailureMessage(failure)), false);
    }
}

package com.mengsama.mod.mengsamanetmusic.client.audio;

import com.mengsama.mod.mengsamanetmusic.api.SongInfo;
import com.mengsama.mod.mengsamanetmusic.init.ModSounds;
import com.mengsama.mod.mengsamanetmusic.network.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.*;
import net.minecraft.client.sounds.*;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import java.net.URL;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;
import javax.sound.sampled.AudioFormat;

 
public abstract class StreamingDeviceSound extends AbstractTickableSoundInstance {
    protected final URL url;
    protected final String targetId;
    protected final long playbackGeneration;
    protected final SongInfo songInfo;
    protected final int countTick;
    protected int tick;
    private boolean refreshAttempted;
    private volatile boolean released;
    private final PlaybackStreamLease streams = new PlaybackStreamLease(this::current, net.minecraft.Util.backgroundExecutor());
    private volatile boolean channelStarted;
    public final boolean mayStartChannel() {
        return current() && ClientMusicPlayback.isCurrentSound(targetId, playbackGeneration, this);
    }
    public final void markChannelStarted() { channelStarted = true; }
    public final boolean listeningReady() { return channelStarted && !released && tick < countTick; }
    protected StreamingDeviceSound(URL source, int seconds, String target, long generation, SongInfo song, int start) {
        super(ModSounds.NET_MUSIC.get(), SoundSource.RECORDS, SoundInstance.createUnseededRandom());
        url = source;
        targetId = target;
        playbackGeneration = generation;
        songInfo = song == null ? new SongInfo(source.toExternalForm(), "", seconds) : song.clone();
        countTick = (int)Math.min(Integer.MAX_VALUE - 200L, Math.max(0L, seconds) * 20);
        tick = (int)Math.min(countTick, Math.max(0L, start) * 20);
        attenuation = Attenuation.NONE;
    }
    protected final void relocate(Vec3 position) { x = position.x; y = position.y; z = position.z; }
    protected abstract boolean updateEmitter();
    protected abstract int endGraceTicks();
    protected abstract RefreshPlaybackPacket refreshPacket(long generation, long nonce);
    protected abstract void reportFailure(Throwable failure);
    protected boolean emitsNotes() { return true; }
    protected double noteHeight() { return 1; }
    @Override public final void tick() {
        if (!current() || Minecraft.getInstance().level == null) { release(); return; }
        if (!updateEmitter()) { release(); return; }
        if (ClientMusicPlayback.isPaused(targetId)) return;
        if (++tick > (long)countTick + endGraceTicks()) { release(); return; }
        var level = Minecraft.getInstance().level;
        if (emitsNotes() && level.getGameTime() % 8 == 0) {
             
            level.addParticle(net.minecraft.core.particles.ParticleTypes.NOTE, x, y + noteHeight(), z,
                    (level.getGameTime() % 24) / 24D, 0, 0);
        }
    }
    private boolean current() { return !released && ClientMusicPlayback.isCurrent(targetId, playbackGeneration); }
    protected final void release() {
        if (released) return;
        released = true;
        streams.close();
        stop();
        try { Minecraft.getInstance().getSoundManager().stop(this); } catch (RuntimeException ignored) {}
        if (ClientMusicPlayback.unregister(targetId, this))
            com.mengsama.mod.mengsamanetmusic.hud.MusicInfoHud.onDeviceStopped(targetId);
    }
    public int getTick() { return tick; }
    public URL getUrl() { return url; }
    public int getDurationSeconds() { return countTick / 20; }
    public SongInfo getSongInfo() { return songInfo.clone(); }
    @Override public final CompletableFuture<AudioStream> getStream(SoundBufferLibrary buffers, Sound sound, boolean looping) {
        int offset = tick / 20;
        return CompletableFuture.supplyAsync(() -> open(offset), net.minecraft.Util.backgroundExecutor());
    }
    private AudioStream open(int offset) {
        if (!current()) return new EmptyAudio();
        try {
            var stream = new NetMusicAudioStream(url, songInfo.playbackHeaders, offset, () -> !current());
            return streams.attach(stream);
        } catch (Exception failure) {
            Minecraft.getInstance().execute(() -> recover(failure));
        }
        return new EmptyAudio();
    }
    private void recover(Exception failure) {
         
        if (!current()) return;
        long failed = ClientMusicPlayback.serverGeneration(targetId);
        boolean refreshable = !refreshAttempted && NetMusicAudioStream.shouldRefreshProvider(songInfo, failure);
        release();
        if (refreshable) {
            refreshAttempted = true;
            long nonce = ClientMusicPlayback.beginRefresh(targetId, failed);
            if (nonce != 0) ModNetwork.CHANNEL.sendToServer(refreshPacket(failed, nonce));
            return;
        }
        reportFailure(failure);
    }
    private static final class EmptyAudio implements AudioStream {
        private static final AudioFormat FORMAT = new AudioFormat(44100, 16, 2, true, false);
        public AudioFormat getFormat() { return FORMAT; }
        public ByteBuffer read(int bytes) { return null; }
        public void close() {}
    }
}

package com.mengsama.mod.mengsamanetmusic.network;

import com.mengsama.mod.mengsamanetmusic.api.SongInfo;
import com.mengsama.mod.mengsamanetmusic.client.audio.ClientMusicPlayback;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.network.NetworkEvent;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.function.Supplier;

public class PlayMusicPacket {
    private final BlockPos pos;
    private final String targetId;
    private final String url;
    private final String rawUrl;
    private final int timeSecond;
    private final String songName;
    private final long requestGeneration;
    private final long refreshNonce;
    private final SongInfo info;
    private final int startSecond;

    public PlayMusicPacket(BlockPos pos, String targetId, String url, String rawUrl, int timeSecond, String songName, long requestGeneration, SongInfo info) {
        this(pos, targetId, url, rawUrl, timeSecond, songName, requestGeneration, 0L, info, 0);
    }

    public PlayMusicPacket(BlockPos pos, String targetId, String url, String rawUrl, int timeSecond, String songName, long requestGeneration, long refreshNonce, SongInfo info) {
        this(pos, targetId, url, rawUrl, timeSecond, songName, requestGeneration, refreshNonce, info, 0);
    }

    public PlayMusicPacket(BlockPos pos, String targetId, String url, String rawUrl, int timeSecond, String songName, long requestGeneration, long refreshNonce, SongInfo info, int startSecond) {
        this.pos = pos;
        this.targetId = targetId;
        this.url = url;
        this.rawUrl = rawUrl;
        this.timeSecond = timeSecond;
        this.songName = songName;
        this.requestGeneration = requestGeneration;
        this.refreshNonce = refreshNonce;
        this.info = info == null ? new SongInfo(rawUrl, songName, timeSecond) : info.clone();
        this.startSecond = Math.max(0, Math.min(timeSecond, startSecond));
    }

    public static PlayMusicPacket decode(FriendlyByteBuf buf) {
        BlockPos pos = BlockPos.of(buf.readLong());
        String targetId = buf.readUtf();
        String url = buf.readUtf();
        String rawUrl = buf.readUtf();
        int timeSecond = buf.readInt();
        String songName = buf.readUtf();
        long requestGeneration = buf.readLong();
        long refreshNonce = buf.readLong();
        SongInfo info = SongInfo.deserializeNBT(buf.readNbt());
        if (info == null) {
            info = new SongInfo(rawUrl, songName, timeSecond);
        }
        info.playbackHeaders.putAll(PlayerPlayMusicPacket.readHeaders(buf));
        int startSecond = buf.readInt();
        return new PlayMusicPacket(pos, targetId, url, rawUrl, timeSecond, songName, requestGeneration, refreshNonce, info, startSecond);
    }

    public static void encode(PlayMusicPacket message, FriendlyByteBuf buf) {
        buf.writeLong(message.pos.asLong());
        buf.writeUtf(message.targetId);
        buf.writeUtf(message.url);
        buf.writeUtf(message.rawUrl);
        buf.writeInt(message.timeSecond);
        buf.writeUtf(message.songName);
        buf.writeLong(message.requestGeneration);
        buf.writeLong(message.refreshNonce);
        var tag = new net.minecraft.nbt.CompoundTag();
        SongInfo.serializeNBT(message.info, tag);
        buf.writeNbt(tag);
        PlayerPlayMusicPacket.writeHeaders(buf, message.info.playbackHeaders);
        buf.writeInt(message.startSecond);
    }

    public static void handle(PlayMusicPacket message, Supplier<NetworkEvent.Context> contextSupplier) {
        ClientPacketDispatch.accept(contextSupplier, () -> onHandle(message));
    }

    @OnlyIn(Dist.CLIENT)
    private static void onHandle(PlayMusicPacket message) {
        if (!ClientMusicPlayback.acceptServerGeneration(message.targetId, message.requestGeneration, message.refreshNonce)) return;
        if (message.refreshNonce == 0L && ClientMusicPlayback.consumeSeekResponse(
                message.targetId, message.startSecond, message.info.identityKey())) return;
        try {
            URL songUrl = new URI(message.url).toURL();
            long generation = ClientMusicPlayback.beginSwitch(message.targetId);

            net.minecraft.world.level.Level level = Minecraft.getInstance().level;
            if (level != null && level.getBlockEntity(message.pos) instanceof com.mengsama.mod.mengsamanetmusic.block.IMusicPlayerBlockEntity be) {
                be.setPlay(true);
            }
            SongInfo hudInfo = message.info.clone();
            hudInfo.songUrl = message.rawUrl;
            hudInfo.songName = message.songName;
            hudInfo.songTime = message.timeSecond;
            hudInfo.normalizeIdentity();
            com.mengsama.mod.mengsamanetmusic.util.NetMusicSound sound =
                    new com.mengsama.mod.mengsamanetmusic.util.NetMusicSound(message.pos, songUrl,
                            message.timeSecond, message.targetId, generation, hudInfo, message.startSecond);
            ClientMusicPlayback.register(message.targetId, sound);
            Minecraft.getInstance().getSoundManager().play(sound);
             
            if (ClientMusicPlayback.isPaused(message.targetId)) {
                ClientMusicPlayback.setPaused(message.targetId, true);
            }

            if (message.targetId.startsWith("backpack:")) com.mengsama.mod.mengsamanetmusic.hud.MusicInfoHud.setInfoFromPacket(message.targetId, sound.getSongInfo());
             
            Minecraft.getInstance().gui.setNowPlaying(Component.literal(message.songName));
        } catch (MalformedURLException | URISyntaxException ignored) {
        } catch (Exception e) {
            com.mengsama.mod.mengsamanetmusic.MengSamaNetMusic.LOGGER.error("PlayMusicPacket onHandle error: {}", e.getMessage());
        }
    }

    public String statisticsTarget() { return targetId; }
    public SongInfo statisticsSong() { return info.clone(); }
    public long statisticsGeneration() { return requestGeneration; }
    public long statisticsRefresh() { return refreshNonce; }
    public int statisticsStart() { return startSecond; }

    public BlockPos getPos() {
        return pos;
    }

    public String getUrl() {
        return url;
    }

    public String getRawUrl() {
        return rawUrl;
    }

    public int getTimeSecond() {
        return timeSecond;
    }

    public String getSongName() {
        return songName;
    }
}

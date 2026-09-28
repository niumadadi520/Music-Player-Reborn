package com.mengsama.mod.mengsamanetmusic.network;

import com.mengsama.mod.mengsamanetmusic.MengSamaNetMusic;
import com.mengsama.mod.mengsamanetmusic.api.SongInfo;
import com.mengsama.mod.mengsamanetmusic.client.audio.ClientMusicPlayback;
import com.mengsama.mod.mengsamanetmusic.hud.MusicInfoHud;
import com.mengsama.mod.mengsamanetmusic.item.MusicPlayerItem;
import com.mengsama.mod.mengsamanetmusic.util.PlayerNetMusicSound;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import com.mengsama.mod.mengsamanetmusic.platform.PacketContext;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.function.Supplier;

public record PlayerPlayMusicPacket(int playerID, String targetId, String url, int timeSecond, String songName, int slot, long requestGeneration, long refreshNonce, SongInfo info, boolean maidSource, boolean broadcast, int startSecond) {

    public PlayerPlayMusicPacket(int playerID, String targetId, String url, int timeSecond, String songName, int slot, long requestGeneration, SongInfo info) {
        this(playerID, targetId, url, timeSecond, songName, slot, requestGeneration, 0L, info, false, false, 0);
    }

    public PlayerPlayMusicPacket(int playerID, String targetId, String url, int timeSecond, String songName, int slot, long requestGeneration, SongInfo info, boolean maidSource) {
        this(playerID, targetId, url, timeSecond, songName, slot, requestGeneration, 0L, info, maidSource, false, 0);
    }

    public PlayerPlayMusicPacket(int playerID, String targetId, String url, int timeSecond, String songName, int slot,
                                 long requestGeneration, long refreshNonce, SongInfo info, boolean maidSource, boolean broadcast) {
        this(playerID, targetId, url, timeSecond, songName, slot, requestGeneration, refreshNonce, info, maidSource, broadcast, 0);
    }

    public PlayerPlayMusicPacket(int playerID, String targetId, String url, int timeSecond, String songName, int slot,
                                 long requestGeneration, long refreshNonce, SongInfo info, boolean maidSource, boolean broadcast,
                                 int startSecond) {
        this.playerID = playerID;
        this.targetId = targetId;
        this.url = url;
        this.timeSecond = timeSecond;
        this.songName = songName;
        this.slot = slot;
        this.requestGeneration = requestGeneration;
        this.refreshNonce = refreshNonce;
        this.info = info;
        this.maidSource = maidSource;
        this.broadcast = broadcast;
        this.startSecond = Math.max(0, Math.min(timeSecond, startSecond));
    }

    public PlayerPlayMusicPacket(int playerID, String url, int timeSecond, int slot) {
        this(playerID, "legacy-player:" + playerID, url, timeSecond, "", slot, 0L, 0L, new SongInfo(url, "", timeSecond), false, false, 0);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeInt(playerID);
        buf.writeUtf(targetId);
        buf.writeUtf(url);
        buf.writeInt(timeSecond);
        buf.writeUtf(songName);
        buf.writeInt(slot);
        buf.writeLong(requestGeneration);
        buf.writeLong(refreshNonce);
        var tag = new CompoundTag();
        SongInfo.serializeNBT(info, tag);
        buf.writeNbt(tag);
        writeHeaders(buf, info == null ? java.util.Collections.emptyMap() : info.playbackHeaders);
        buf.writeBoolean(maidSource);
        buf.writeBoolean(broadcast);
        buf.writeInt(startSecond);
    }

    public static PlayerPlayMusicPacket decode(FriendlyByteBuf buf) {
        int playerId = buf.readInt();
        String targetId = buf.readUtf();
        String url = buf.readUtf();
        int timeSecond = buf.readInt();
        String songName = buf.readUtf();
        int slot = buf.readInt();
        long requestGeneration = buf.readLong();
        long refreshNonce = buf.readLong();
        CompoundTag tag = buf.readNbt();
        SongInfo info = SongInfo.deserializeNBT(tag);
        info.playbackHeaders.putAll(readHeaders(buf));
        boolean maidSource = buf.readBoolean();
        boolean broadcast = buf.readBoolean();
        int startSecond = buf.readInt();
        return new PlayerPlayMusicPacket(playerId, targetId, url, timeSecond, songName, slot, requestGeneration, refreshNonce, info, maidSource, broadcast, startSecond);
    }

    static void writeHeaders(FriendlyByteBuf buf, java.util.Map<String, String> headers) {
        java.util.Map<String, String> safe = new java.util.LinkedHashMap<>();
        if (headers != null) headers.forEach((key, value) -> {
             
            if (key != null && value != null && (key.equalsIgnoreCase("User-Agent")
                    || key.equalsIgnoreCase("Referer") || key.equalsIgnoreCase("Accept"))) safe.put(key, value);
        });
        buf.writeVarInt(Math.min(safe.size(), 16));
        safe.entrySet().stream().limit(16).forEach(entry -> {
            buf.writeUtf(entry.getKey(), 64);
            buf.writeUtf(entry.getValue(), 8192);
        });
    }

    static java.util.Map<String, String> readHeaders(FriendlyByteBuf buf) {
        int count = Math.min(buf.readVarInt(), 16);
        java.util.Map<String, String> headers = new java.util.HashMap<>();
        for (int i = 0; i < count; i++) headers.put(buf.readUtf(64), buf.readUtf(8192));
        return headers;
    }

    public static void handle(PlayerPlayMusicPacket packet, Supplier<PacketContext> ctx) {
        ClientPacketDispatch.accept(ctx, () -> {
            com.mengsama.mod.mengsamanetmusic.earbuds.client.EarbudClient.forget(packet.targetId);
            handleClient(packet);
        });
    }

    @OnlyIn(Dist.CLIENT)
    public static void handleClient(PlayerPlayMusicPacket packet) {
        var mc = Minecraft.getInstance();
        if (mc.level == null) return;
        if (!ClientMusicPlayback.acceptServerGeneration(packet.targetId, packet.requestGeneration, packet.refreshNonce)) {
            MengSamaNetMusic.LOGGER.debug("Discarding stale playback response target={} generation={}", packet.targetId, packet.requestGeneration);
            return;
        }
        String responseIdentity = packet.info == null ? "" : packet.info.identityKey();
        if (packet.refreshNonce == 0L && ClientMusicPlayback.consumeSeekResponse(
                packet.targetId, packet.startSecond, responseIdentity)) return;
        try {
            MengSamaNetMusic.LOGGER.info("[播放阶段] 客户端接收 target={} entity={} host={}",
                    packet.targetId, packet.playerID, safeUrlHost(packet.url));
            long generation = ClientMusicPlayback.beginSwitch(packet.targetId);
            URL songUrl = new URL(packet.url);
            var target = com.mengsama.mod.mengsamanetmusic.earbuds.client.EarbudClient.isPrivate(packet.targetId) ? mc.player : mc.level.getEntity(packet.playerID);
            if (target instanceof LivingEntity living) {
                ItemStack playerItem = living instanceof Player player
                        ? findTargetPlayerItem(player, packet.targetId)
                        : com.mengsama.mod.mengsamanetmusic.compat.EntityMusicDevice.heldPlayer(living);
                if (!playerItem.isEmpty()) MusicPlayerItem.setPlay(playerItem, true);
                SongInfo hudInfo = packet.info == null
                        ? new SongInfo(packet.url, packet.songName, packet.timeSecond)
                        : packet.info.clone();
                hudInfo.songName = packet.songName;
                hudInfo.songTime = packet.timeSecond;
                hudInfo.normalizeIdentity();
                hudInfo.playbackHeaders.putAll(packet.info == null ? java.util.Collections.emptyMap() : packet.info.playbackHeaders);
                PlayerNetMusicSound sound = new PlayerNetMusicSound(living, songUrl, packet.timeSecond,
                        packet.slot, packet.targetId, generation, hudInfo, packet.startSecond);
                ClientMusicPlayback.register(packet.targetId, sound);
                mc.getSoundManager().play(sound);
                 
                 
                if (ClientMusicPlayback.isPaused(packet.targetId)) ClientMusicPlayback.setPaused(packet.targetId, true);
                MengSamaNetMusic.LOGGER.info("[播放阶段] 音频启动已提交 target={} initialVolume={}", packet.targetId, sound.getInitialVolume());
                boolean physicalMaid = com.mengsama.mod.mengsamanetmusic.compat.MaidMusicAccess.isMaid(living);
                boolean localOwner = mc.player != null && living.getUUID().equals(mc.player.getUUID());
                if (physicalMaid || com.mengsama.mod.mengsamanetmusic.compat.PlaybackTargetId.isMaidTarget(packet.targetId)) {
                    MusicInfoHud.onMaidDevicePlaying(packet.targetId);
                } else if (localOwner || packet.broadcast) {
                    mc.gui.setNowPlaying(Component.literal(packet.songName));
                    MusicInfoHud.setInfoFromPacket(packet.targetId, sound.getSongInfo());
                } else {
                    MusicInfoHud.clearTarget(packet.targetId);
                }
            } else {
                showClientFailure("找不到音乐声源实体");
            }
        } catch (MalformedURLException e) {
            MengSamaNetMusic.LOGGER.error("[播放阶段] 音频启动异常 target={} reason=invalid-url", packet.targetId);
            showClientFailure("音乐地址无效");
        } catch (Exception e) {
            MengSamaNetMusic.LOGGER.error("[播放阶段] 音频启动异常 target={}", packet.targetId, e);
            showClientFailure("音频启动异常，请查看日志");
        }
    }

    private static String safeUrlHost(String value) {
        try { return new URL(value).getHost(); } catch (Exception ignored) { return "invalid"; }
    }

    @OnlyIn(Dist.CLIENT)
    private static void showClientFailure(String reason) {
        var player = Minecraft.getInstance().player;
        if (player != null) player.displayClientMessage(Component.literal("音乐播放失败：" + reason), false);
    }

    private static ItemStack findTargetPlayerItem(Player player, String targetId) {
        java.util.UUID expected = com.mengsama.mod.mengsamanetmusic.compat.PlaybackTargetId.instanceId(targetId);
        if (expected != null) {
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                ItemStack candidate = player.getInventory().getItem(i);
                if (candidate.getItem() instanceof MusicPlayerItem
                        && expected.equals(MusicPlayerItem.getInstanceId(candidate))) return candidate;
            }
        }
        ItemStack backpackDevice = com.mengsama.mod.mengsamanetmusic.compat.PortableDevices.find(player, expected, false);
        if (!backpackDevice.isEmpty()) return backpackDevice;
         
        return targetId != null && targetId.startsWith("legacy-player:")
                ? MusicPlayerItem.findMusicPlayerItem(player) : ItemStack.EMPTY;
    }

}

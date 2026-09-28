package com.mengsama.mod.mengsamanetmusic.network;

import com.mengsama.mod.mengsamanetmusic.MengSamaNetMusic;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Optional;

public class ModNetwork {
    private static final String VERSION = "7";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(MengSamaNetMusic.MOD_ID, "network"),
            () -> VERSION, it -> it.equals(VERSION), it -> it.equals(VERSION));

    public static void init() {
        bind(0, com.mengsama.mod.mengsamanetmusic.earbuds.EarbudActionPacket.class, com.mengsama.mod.mengsamanetmusic.earbuds.EarbudActionPacket::encode, com.mengsama.mod.mengsamanetmusic.earbuds.EarbudActionPacket::decode, com.mengsama.mod.mengsamanetmusic.earbuds.EarbudActionPacket::handle, NetworkDirection.PLAY_TO_SERVER);
        bind(1, com.mengsama.mod.mengsamanetmusic.earbuds.EarbudStatePacket.class, com.mengsama.mod.mengsamanetmusic.earbuds.EarbudStatePacket::encode, com.mengsama.mod.mengsamanetmusic.earbuds.EarbudStatePacket::decode, com.mengsama.mod.mengsamanetmusic.earbuds.EarbudStatePacket::handle, NetworkDirection.PLAY_TO_CLIENT);
        bind(2, com.mengsama.mod.mengsamanetmusic.earbuds.EarbudAudioPacket.class, com.mengsama.mod.mengsamanetmusic.earbuds.EarbudAudioPacket::encode, com.mengsama.mod.mengsamanetmusic.earbuds.EarbudAudioPacket::decode, com.mengsama.mod.mengsamanetmusic.earbuds.EarbudAudioPacket::handle, NetworkDirection.PLAY_TO_CLIENT);

        bind(3, BackpackMusicActionPacket.class, BackpackMusicActionPacket::encode, BackpackMusicActionPacket::decode, BackpackMusicActionPacket::handle, NetworkDirection.PLAY_TO_SERVER);

        bind(4, PlayMusicPacket.class,
                PlayMusicPacket::encode, PlayMusicPacket::decode, PlayMusicPacket::handle,
                NetworkDirection.PLAY_TO_CLIENT);
        bind(5, PlayerPlayMusicPacket.class,
                PlayerPlayMusicPacket::encode, PlayerPlayMusicPacket::decode, PlayerPlayMusicPacket::handle,
                NetworkDirection.PLAY_TO_CLIENT);
        bind(6, UpdateMusicTickPacket.class,
                UpdateMusicTickPacket::encode, UpdateMusicTickPacket::decode, UpdateMusicTickPacket::handle,
                NetworkDirection.PLAY_TO_SERVER);
        bind(7, StopMusicPacketServer.class,
                StopMusicPacketServer::encode, StopMusicPacketServer::decode, StopMusicPacketServer::handle,
                NetworkDirection.PLAY_TO_SERVER);
        bind(8, PlayerAddSongPacket.class,
                PlayerAddSongPacket::encode, PlayerAddSongPacket::decode, PlayerAddSongPacket::handle,
                NetworkDirection.PLAY_TO_SERVER);
        bind(9, BlockAddSongPacket.class,
                BlockAddSongPacket::encode, BlockAddSongPacket::decode, BlockAddSongPacket::handle,
                NetworkDirection.PLAY_TO_SERVER);
        bind(10, PlayerRemoveSongPacket.class,
                PlayerRemoveSongPacket::encode, PlayerRemoveSongPacket::decode, PlayerRemoveSongPacket::handle,
                NetworkDirection.PLAY_TO_SERVER);

        bind(11, StopMusicPacketClient.class,
                StopMusicPacketClient::encode, StopMusicPacketClient::decode, StopMusicPacketClient::handle,
                NetworkDirection.PLAY_TO_CLIENT);
        bind(12, PauseMusicPacketClient.class,
                PauseMusicPacketClient::encode, PauseMusicPacketClient::decode, PauseMusicPacketClient::handle,
                NetworkDirection.PLAY_TO_CLIENT);
        bind(13, SyncVipCookiePacket.class,
                SyncVipCookiePacket::encode, SyncVipCookiePacket::decode, SyncVipCookiePacket::handle,
                NetworkDirection.PLAY_TO_CLIENT);
        bind(14, OpenMaidMusicPacket.class,
                OpenMaidMusicPacket::encode, OpenMaidMusicPacket::decode, OpenMaidMusicPacket::handle,
                NetworkDirection.PLAY_TO_SERVER);
        bind(15, MaidDeviceSyncPacket.class,
                MaidDeviceSyncPacket::encode, MaidDeviceSyncPacket::decode, MaidDeviceSyncPacket::handle,
                NetworkDirection.PLAY_TO_CLIENT);
        bind(16, ReturnToMaidGuiPacket.class,
                ReturnToMaidGuiPacket::encode, ReturnToMaidGuiPacket::decode, ReturnToMaidGuiPacket::handle,
                NetworkDirection.PLAY_TO_SERVER);
        bind(17, RefreshPlaybackPacket.class,
                RefreshPlaybackPacket::encode, RefreshPlaybackPacket::decode, RefreshPlaybackPacket::handle,
                NetworkDirection.PLAY_TO_SERVER);
        bind(18, SeekPlaybackPacket.class,
                SeekPlaybackPacket::encode, SeekPlaybackPacket::decode, SeekPlaybackPacket::handle,
                NetworkDirection.PLAY_TO_SERVER);
        bind(19, PlayerHudVisibilityPacket.class,
                PlayerHudVisibilityPacket::encode, PlayerHudVisibilityPacket::decode, PlayerHudVisibilityPacket::handle,
                NetworkDirection.PLAY_TO_CLIENT);
    }

    private static final java.util.Set<Integer> REGISTERED = new java.util.HashSet<>();
    private static synchronized <T> void bind(int id, Class<T> type,
            java.util.function.BiConsumer<T, net.minecraft.network.FriendlyByteBuf> encoder,
            java.util.function.Function<net.minecraft.network.FriendlyByteBuf, T> decoder,
            java.util.function.BiConsumer<T, java.util.function.Supplier<net.minecraftforge.network.NetworkEvent.Context>> handler,
            NetworkDirection direction) {
        if (!REGISTERED.add(id)) throw new IllegalStateException("Duplicate music packet ID: " + id);
        CHANNEL.registerMessage(id, type, encoder, decoder, handler, Optional.of(direction));
    }

    public static void sendToNearby(Level world, BlockPos pos, Object message) {
        if (!(world instanceof ServerLevel server)) return;
        double radiusSquared = 96D * 96D;
        for (ServerPlayer listener : server.players()) {
            if (listener.distanceToSqr(pos.getX(), pos.getY(), pos.getZ()) >= radiusSquared) continue;
            sendToClientPlayer(message, listener);
        }
    }

    public static void sendToClientPlayer(Object message, ServerPlayer player) {
        com.mengsama.mod.mengsamanetmusic.listening.ListeningServer.delivered(message, player);
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), message);
    }
}

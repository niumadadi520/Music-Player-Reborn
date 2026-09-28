package com.mengsama.mod.mengsamanetmusic.network;

import com.mengsama.mod.mengsamanetmusic.block.IMusicPlayerBlockEntity;
import com.mengsama.mod.mengsamanetmusic.item.MusicPlayerItem;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

 
public record SeekPlaybackPacket(int entityId, BlockPos blockPos, String targetId, int second, String songIdentity) {
    public SeekPlaybackPacket(int entityId, BlockPos blockPos, String targetId, int second) {
        this(entityId, blockPos, targetId, second, "");
    }

    public static void encode(SeekPlaybackPacket packet, FriendlyByteBuf buf) {
        buf.writeInt(packet.entityId);
        buf.writeBoolean(packet.blockPos != null);
        if (packet.blockPos != null) buf.writeBlockPos(packet.blockPos);
        buf.writeUtf(packet.targetId, 512);
        buf.writeInt(packet.second);
        buf.writeUtf(packet.songIdentity == null ? "" : packet.songIdentity, 512);
    }

    public static SeekPlaybackPacket decode(FriendlyByteBuf buf) {
        int entityId = buf.readInt();
        BlockPos pos = buf.readBoolean() ? buf.readBlockPos() : null;
        return new SeekPlaybackPacket(entityId, pos, buf.readUtf(512), buf.readInt(), buf.readUtf(512));
    }

    public static void handle(SeekPlaybackPacket packet, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        ServerPlayer sender = context.getSender();
        if (sender != null) context.enqueueWork(() -> apply(packet, sender));
        context.setPacketHandled(true);
    }

    private static void apply(SeekPlaybackPacket packet, ServerPlayer sender) {
        int second = Math.max(0, packet.second());
        if (sender.containerMenu instanceof com.mengsama.mod.mengsamanetmusic.gui.MusicPlayerMenu backpackMenu && backpackMenu.getBackpackBinding() != null) {
            var device = backpackMenu.resolveValidatedDevice(sender);
            if (device.isEmpty() || !MusicPlayerItem.isPlay(device) || !backpackMenu.getTargetId().equals(packet.targetId())) return;
            var currentSong = song(MusicPlayerItem.getCurrentCd(device));
            if (currentSong != null && matchesIdentity(packet.songIdentity(), currentSong)) {
        com.mengsama.mod.mengsamanetmusic.listening.ListeningServer.preserveNext(sender, packet.targetId());
                backpackMenu.getBackpackBinding().play(sender, currentSong, Math.min(second, Math.max(0, currentSong.songTime)), MusicPlayerItem.isPaused(device));
                backpackMenu.syncAuthoritativeState(sender);
            }
            return;
        }
        if (packet.blockPos() != null) {
            if (!(sender.containerMenu instanceof com.mengsama.mod.mengsamanetmusic.gui.MusicPlayerPlaylistMenu menu)
                    || !menu.stillValid(sender) || !packet.blockPos().equals(menu.getBlockEntity().getBlockPos())) return;
            IMusicPlayerBlockEntity player = menu.getBlockEntity();
            if (player == null || !player.isPlay()
                    || !packet.targetId().equals(player.blockTargetId())) return;
            ItemStack cd = player.getCurrentCd();
            var song = song(cd);
            if (song == null || !song.sameIdentity(song(player.getCurrentCd()))
                    || !matchesIdentity(packet.songIdentity(), song)) return;
            int duration = Math.max(0, song.songTime);
            second = Math.min(second, duration);
            boolean paused = player.isPaused();
            player.setCurrentTime(Math.max(1, (duration - second) * 20 + 64));
            player.markDirty();
        com.mengsama.mod.mengsamanetmusic.listening.ListeningServer.preserveNext(sender, packet.targetId());
            player.seekToClient(song, second, paused);
            return;
        }
        if (!(sender.containerMenu instanceof com.mengsama.mod.mengsamanetmusic.gui.MusicPlayerMenu menu)) return;
        ItemStack device = menu.resolveValidatedDevice(sender);
        if (device.isEmpty() || !MusicPlayerItem.isPlay(device) || !menu.getTargetId().equals(packet.targetId())) return;
        var entity = sender.level().getEntity(packet.entityId());
        if (!(entity instanceof LivingEntity living)) return;
        if (menu.isPlayerHandContext() ? living != sender : living.getId() != menu.getBoundRuntimeEntityId()) return;
        var song = song(MusicPlayerItem.getCurrentCd(device));
        if (song == null || !matchesIdentity(packet.songIdentity(), song)) return;
        second = Math.min(second, Math.max(0, song.songTime));
        boolean paused = MusicPlayerItem.isPaused(device);
        MusicPlayerItem.setCurrentTime(device, Math.max(1, (song.songTime - second) * 20 + 64));
        com.mengsama.mod.mengsamanetmusic.listening.ListeningServer.preserveNext(sender, packet.targetId());
        if (living instanceof ServerPlayer player) {
            MusicPlayerItem.seekToClient(device, song, player, second, paused);
        } else {
            MusicPlayerItem.seekToEntity(device, song, living, second, paused);
        }
    }

    static boolean matchesIdentity(String requestedIdentity,
                                   com.mengsama.mod.mengsamanetmusic.api.SongInfo song) {
        return requestedIdentity == null || requestedIdentity.isBlank()
                || song != null && requestedIdentity.equals(song.identityKey());
    }

    private static com.mengsama.mod.mengsamanetmusic.api.SongInfo song(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        if (stack.getItem() instanceof com.mengsama.mod.mengsamanetmusic.item.MusicListItem)
            return com.mengsama.mod.mengsamanetmusic.item.MusicListItem.getSongInfo(stack);
        return null;
    }
}

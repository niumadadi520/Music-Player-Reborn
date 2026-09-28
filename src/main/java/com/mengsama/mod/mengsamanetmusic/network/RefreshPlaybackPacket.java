package com.mengsama.mod.mengsamanetmusic.network;

import com.mengsama.mod.mengsamanetmusic.api.SongInfo;
import com.mengsama.mod.mengsamanetmusic.compat.EntityMusicDevice;
import com.mengsama.mod.mengsamanetmusic.item.MusicPlayerItem;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import com.mengsama.mod.mengsamanetmusic.platform.PacketContext;

import java.util.function.Supplier;

 
public record RefreshPlaybackPacket(int entityId, BlockPos blockPos, String targetId, long requestGeneration, long requestNonce, SongInfo song) {
    public RefreshPlaybackPacket(int entityId, String targetId, long requestGeneration, long requestNonce, SongInfo song) {
        this(entityId, null, targetId, requestGeneration, requestNonce, song);
    }

    public RefreshPlaybackPacket(BlockPos blockPos, String targetId, long requestGeneration, long requestNonce, SongInfo song) {
        this(-1, blockPos, targetId, requestGeneration, requestNonce, song);
    }

    public static void encode(RefreshPlaybackPacket packet, FriendlyByteBuf buf) {
        buf.writeInt(packet.entityId);
        buf.writeBoolean(packet.blockPos != null);
        if (packet.blockPos != null) buf.writeBlockPos(packet.blockPos);
        buf.writeUtf(packet.targetId, 512);
        buf.writeLong(packet.requestGeneration);
        buf.writeLong(packet.requestNonce);
        CompoundTag tag = new CompoundTag();
        SongInfo.serializeNBT(packet.song, tag);
        buf.writeNbt(tag);
    }

    public static RefreshPlaybackPacket decode(FriendlyByteBuf buf) {
        int entityId = buf.readInt();
        BlockPos blockPos = buf.readBoolean() ? buf.readBlockPos() : null;
        String targetId = buf.readUtf(512);
        long requestGeneration = buf.readLong();
        long requestNonce = buf.readLong();
        return new RefreshPlaybackPacket(entityId, blockPos, targetId, requestGeneration, requestNonce,
                SongInfo.deserializeNBT(buf.readNbt()));
    }

    public static void handle(RefreshPlaybackPacket packet, Supplier<PacketContext> supplier) {
        PacketContext context = supplier.get();
        var sender = context.getSender();
        if (sender != null) context.enqueueWork(() -> {
            if (com.mengsama.mod.mengsamanetmusic.earbuds.EarbudSessions.refreshShared(sender, packet)) return;
            if (com.mengsama.mod.mengsamanetmusic.compat.BackpackAccess.refresh(sender, packet.blockPos, packet.targetId, packet.requestGeneration, packet.requestNonce, packet.song)) return;
            if (packet.blockPos != null) {
                if (sender.distanceToSqr(packet.blockPos.getX() + .5, packet.blockPos.getY() + .5, packet.blockPos.getZ() + .5) > 96D * 96D) return;
                if (!sender.level().hasChunkAt(packet.blockPos)) return;
                var blockEntity = sender.level().getBlockEntity(packet.blockPos);
                SongInfo authoritative = null;
                if (blockEntity instanceof com.mengsama.mod.mengsamanetmusic.block.MusicPlayerBlockEntity player && player.isPlay()) {
                    authoritative = songFromCd(player.getCurrentCd());
                    if (packet.targetId.equals(player.blockTargetId()) && authoritative != null
                            && authoritative.sameIdentity(packet.song) && authoritative.canRefreshProvider()
                            && PlaybackRefreshSessions.consume(packet.targetId, packet.requestGeneration,
                            packet.requestNonce, authoritative, packet.targetId)) player.setPlayToClient(authoritative, packet.requestNonce);
                } else if (blockEntity instanceof com.mengsama.mod.mengsamanetmusic.block.PortableMusicPlayerBlockEntity player && player.isPlay()) {
                    authoritative = songFromCd(player.getCurrentCd());
                    if (packet.targetId.equals(player.blockTargetId()) && authoritative != null
                            && authoritative.sameIdentity(packet.song) && authoritative.canRefreshProvider()
                            && PlaybackRefreshSessions.consume(packet.targetId, packet.requestGeneration,
                            packet.requestNonce, authoritative, packet.targetId)) player.setPlayToClient(authoritative, packet.requestNonce);
                }
                return;
            }
            var entity = sender.level().getEntity(packet.entityId);
            if (!(entity instanceof LivingEntity living) || sender.distanceToSqr(entity) > 96D * 96D) return;
            var device = EntityMusicDevice.playbackDevice(living, packet.targetId);
            if (device.isEmpty() || !MusicPlayerItem.isPlay(device)) return;
            SongInfo authoritative = currentSong(device);
            if (authoritative == null || !authoritative.sameIdentity(packet.song) || !authoritative.canRefreshProvider()
                    || !PlaybackRefreshSessions.consume(packet.targetId, packet.requestGeneration,
                    packet.requestNonce, authoritative, sender.getUUID().toString())) return;
            if (living instanceof net.minecraft.server.level.ServerPlayer player)
                MusicPlayerItem.setPlayToClient(device, authoritative, player, packet.requestNonce);
            else MusicPlayerItem.setPlayToEntity(device, authoritative, living, packet.requestNonce);
        });
        
    }

    private static SongInfo songFromCd(net.minecraft.world.item.ItemStack cd) {
        if (cd == null || cd.isEmpty()) return null;
        return cd.getItem() instanceof com.mengsama.mod.mengsamanetmusic.item.MusicListItem
                ? com.mengsama.mod.mengsamanetmusic.item.MusicListItem.getSongInfo(cd) : null;
    }

    private static SongInfo currentSong(net.minecraft.world.item.ItemStack device) {
        var cds = MusicPlayerItem.loadAllCds(device);
        int index = MusicPlayerItem.getPlayIndex(device);
        if (index < 0 || index >= cds.size() || cds.get(index).isEmpty()) return null;
        var cd = cds.get(index);
        return cd.getItem() instanceof com.mengsama.mod.mengsamanetmusic.item.MusicListItem
                ? com.mengsama.mod.mengsamanetmusic.item.MusicListItem.getSongInfo(cd) : null;
    }
}

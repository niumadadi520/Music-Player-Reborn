package com.mengsama.mod.mengsamanetmusic.network;

import com.mengsama.mod.mengsamanetmusic.compat.MaidMusicAccess;
import com.mengsama.mod.mengsamanetmusic.compat.EntityMusicDevice;
import com.mengsama.mod.mengsamanetmusic.compat.TouhouLittleMaidExtension;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

 
public record ReturnToMaidGuiPacket(UUID maidId, int entityId, UUID instanceId) {
    public static void encode(ReturnToMaidGuiPacket packet, FriendlyByteBuf buf) {
        buf.writeUUID(packet.maidId);
        buf.writeInt(packet.entityId);
        buf.writeUUID(packet.instanceId);
    }

    public static ReturnToMaidGuiPacket decode(FriendlyByteBuf buf) {
        return new ReturnToMaidGuiPacket(buf.readUUID(), buf.readInt(), buf.readUUID());
    }

    public static void handle(ReturnToMaidGuiPacket packet, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        ServerPlayer sender = context.getSender();
        if (sender != null) {
            context.enqueueWork(() -> {
                var entity = sender.serverLevel().getEntity(packet.entityId);
                if (entity == null || !entity.getUUID().equals(packet.maidId)
                        || !MaidMusicAccess.mayControl(sender, entity)
                        || EntityMusicDevice.resolve(sender, packet.maidId, packet.entityId, packet.instanceId).isEmpty()) {
                    return;
                }
                MaidMusicAccess.openGui(sender, entity);
            });
        }
        context.setPacketHandled(true);
    }
}

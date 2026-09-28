package com.mengsama.mod.mengsamanetmusic.network;

import com.mengsama.mod.mengsamanetmusic.compat.MaidMusicAccess;
import com.mengsama.mod.mengsamanetmusic.compat.EntityMusicDevice;
import com.mengsama.mod.mengsamanetmusic.compat.TouhouLittleMaidExtension;
import com.mengsama.mod.mengsamanetmusic.gui.MusicPlayerMenu;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import com.mengsama.mod.mengsamanetmusic.platform.PacketContext;


import java.util.UUID;
import java.util.function.Supplier;

public record OpenMaidMusicPacket(UUID maidId, int entityId, UUID instanceId) {
    public static void encode(OpenMaidMusicPacket packet, FriendlyByteBuf buf) {
        buf.writeUUID(packet.maidId); buf.writeInt(packet.entityId); buf.writeUUID(packet.instanceId);
    }
    public static OpenMaidMusicPacket decode(FriendlyByteBuf buf) {
        return new OpenMaidMusicPacket(buf.readUUID(), buf.readInt(), buf.readUUID());
    }
    public static void handle(OpenMaidMusicPacket packet, Supplier<PacketContext> supplier) {
        var context = supplier.get();
        ServerPlayer sender = context.getSender();
        if (sender != null) context.enqueueWork(() -> {
            var stack = EntityMusicDevice.resolve(sender, packet.maidId, packet.entityId, packet.instanceId);
            var entity = sender.serverLevel().getEntity(packet.entityId);
            if (stack.isEmpty() || entity == null || !entity.getUUID().equals(packet.maidId)
                    || !MaidMusicAccess.mayControl(sender, entity)) return;
            sender.openMenu(new SimpleMenuProvider(
                    (id, inv, ignored) -> {
                        MusicPlayerMenu menu = MusicPlayerMenu.forMaid(id, inv, packet.maidId, packet.entityId, packet.instanceId);
                        sender.getServer().execute(() -> menu.syncAuthoritativeState(sender));
                        return menu;
                    },
                    Component.translatable("item.mengsamanetmusic.music_player")),
                    buf -> {
                        buf.writeByte(MusicPlayerMenu.Context.MAID.ordinal());
                        buf.writeUUID(packet.maidId);
                        buf.writeInt(packet.entityId);
                        buf.writeUUID(packet.instanceId);
                    });
        });
        
    }
}

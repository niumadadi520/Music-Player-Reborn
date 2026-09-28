package com.mengsama.mod.mengsamanetmusic.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record UpdateMusicTickPacket(int slot, java.util.UUID instanceId, int tick) {

    public void encode(FriendlyByteBuf buf) {
        buf.writeInt(slot);
        buf.writeUUID(instanceId);
        buf.writeInt(tick);
    }

    public static UpdateMusicTickPacket decode(FriendlyByteBuf buf) {
        return new UpdateMusicTickPacket(buf.readInt(), buf.readUUID(), buf.readInt());
    }

    public static void handle(UpdateMusicTickPacket packet, Supplier<NetworkEvent.Context> ctx) {
        var c = ctx.get();
        if (!c.getDirection().getReceptionSide().isServer()) {
            c.setPacketHandled(true);
            return;
        }
        var player = c.getSender();
        if (player == null) {
            c.setPacketHandled(true);
            return;
        }
        c.enqueueWork(() -> {
            int slot = packet.slot();
            if (slot < 0 || slot >= player.getInventory().getContainerSize()) {
                return;
            }
            var stack = player.getInventory().getItem(slot);
            if (!(stack.getItem() instanceof com.mengsama.mod.mengsamanetmusic.item.MusicPlayerItem)) return;
            if (!com.mengsama.mod.mengsamanetmusic.item.MusicPlayerItem.getOrCreateInstanceId(stack).equals(packet.instanceId())) return;
            stack.getOrCreateTag().putInt("tick", packet.tick());
        });
        c.setPacketHandled(true);
    }
}

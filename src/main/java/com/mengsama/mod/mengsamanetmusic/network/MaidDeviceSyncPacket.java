package com.mengsama.mod.mengsamanetmusic.network;

import com.mengsama.mod.mengsamanetmusic.gui.MusicPlayerMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

 
public record MaidDeviceSyncPacket(int containerId, CompoundTag tag) {
    public static void encode(MaidDeviceSyncPacket packet, FriendlyByteBuf buf) {
        buf.writeInt(packet.containerId);
        buf.writeNbt(packet.tag);
    }

    public static MaidDeviceSyncPacket decode(FriendlyByteBuf buf) {
        int containerId = buf.readInt();
        CompoundTag tag = buf.readNbt();
        return new MaidDeviceSyncPacket(containerId, tag == null ? new CompoundTag() : tag);
    }

    public static void handle(MaidDeviceSyncPacket packet, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> applyClient(packet));
        context.setPacketHandled(true);
    }

    @OnlyIn(Dist.CLIENT)
    private static void applyClient(MaidDeviceSyncPacket packet) {
        var player = Minecraft.getInstance().player;
        if (player != null && player.containerMenu instanceof MusicPlayerMenu menu
                && menu.containerId == packet.containerId) {
            menu.applyAuthoritativeTag(packet.tag);
        }
    }
}

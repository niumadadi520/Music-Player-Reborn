package com.mengsama.mod.mengsamanetmusic.network;

import com.mengsama.mod.mengsamanetmusic.gui.MusicPlayerMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import com.mengsama.mod.mengsamanetmusic.platform.PacketContext;

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

    public static void handle(MaidDeviceSyncPacket packet, Supplier<PacketContext> supplier) {
        PacketContext context = supplier.get();
        context.enqueueWork(() -> applyClient(packet));
        
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

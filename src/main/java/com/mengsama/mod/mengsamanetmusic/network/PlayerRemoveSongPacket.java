package com.mengsama.mod.mengsamanetmusic.network;

import com.mengsama.mod.mengsamanetmusic.platform.PacketContext;

import java.util.function.Supplier;

public class PlayerRemoveSongPacket {
    private final int slotIndex;

    public PlayerRemoveSongPacket(int slotIndex) {
        this.slotIndex = slotIndex;
    }

    public static void encode(PlayerRemoveSongPacket message, net.minecraft.network.FriendlyByteBuf buf) {
        buf.writeInt(message.slotIndex);
    }

    public static PlayerRemoveSongPacket decode(net.minecraft.network.FriendlyByteBuf buf) {
        return new PlayerRemoveSongPacket(buf.readInt());
    }

    public static void handle(PlayerRemoveSongPacket message, Supplier<PacketContext> contextSupplier) {
        ServerPacketDispatch.withPlayer(contextSupplier, sender -> {

                if (sender.containerMenu instanceof com.mengsama.mod.mengsamanetmusic.gui.MusicPlayerMenu menu)
                    menu.removeSong(sender, message.slotIndex);
        });
    }
}

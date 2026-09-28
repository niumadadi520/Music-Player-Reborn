package com.mengsama.mod.mengsamanetmusic.earbuds;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import java.util.UUID;
import java.util.function.Supplier;
public record EarbudActionPacket(int action, UUID id, int choice) {
    public static final UUID NONE = new UUID(0, 0);
    public void encode(FriendlyByteBuf b) { b.writeByte(action); b.writeUUID(id); b.writeVarInt(choice); }
    public static EarbudActionPacket decode(FriendlyByteBuf b) { return new EarbudActionPacket(b.readUnsignedByte(), b.readUUID(), b.readVarInt()); }
    public static void handle(EarbudActionPacket p, Supplier<NetworkEvent.Context> ctx) {
        var c = ctx.get(); c.enqueueWork(() -> { var player = c.getSender(); if (player == null || player.isSpectator()) return;
            switch (p.action) {
                case 0 -> { if (player.containerMenu.containerId == p.choice && player.containerMenu instanceof com.mengsama.mod.mengsamanetmusic.gui.MusicPlayerMenu menu) EarbudMenu.openDevice(player, menu); }
                case 1 -> EarbudSessions.invite(player, p.id, p.choice);
                case 2 -> EarbudSessions.reply(player, p.id, p.choice == 1);
                case 3 -> EarbudSessions.disconnect(player, "已结束一起听");
                case 4 -> { if (player.containerMenu.containerId == p.choice && player.containerMenu instanceof EarbudMenu menu) menu.returnToMusic(player); }
            }
        }); c.setPacketHandled(true);
    }
}

package com.mengsama.mod.mengsamanetmusic.network;

import com.mengsama.mod.mengsamanetmusic.compat.BackpackAccess;
import com.mengsama.mod.mengsamanetmusic.gui.MusicPlayerMenu;
import net.minecraft.network.FriendlyByteBuf;
import com.mengsama.mod.mengsamanetmusic.platform.PacketContext;
import java.util.function.Supplier;

 
public record BackpackMusicActionPacket(int containerId, int upgradeSlot, boolean back) {
    public static void encode(BackpackMusicActionPacket p, FriendlyByteBuf b) { b.writeVarInt(p.containerId); b.writeVarInt(p.upgradeSlot); b.writeBoolean(p.back); }
    public static BackpackMusicActionPacket decode(FriendlyByteBuf b) { return new BackpackMusicActionPacket(b.readVarInt(), b.readVarInt(), b.readBoolean()); }
    public static void handle(BackpackMusicActionPacket p, Supplier<PacketContext> supplier) {
        var context = supplier.get(); var player = context.getSender();
        if (player != null) context.enqueueWork(() -> {
            if (player.containerMenu.containerId != p.containerId || player.isSpectator()) return;
            if (p.back) {
                if (player.containerMenu instanceof MusicPlayerMenu menu && menu.getBackpackBinding() != null && menu.stillValid(player))
                    menu.getBackpackBinding().returnToBackpack(player);
            } else BackpackAccess.open(player, p.containerId, p.upgradeSlot);
        });
        
    }
}

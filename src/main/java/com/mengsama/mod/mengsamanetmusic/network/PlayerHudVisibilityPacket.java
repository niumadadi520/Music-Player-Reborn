package com.mengsama.mod.mengsamanetmusic.network;

import com.mengsama.mod.mengsamanetmusic.client.audio.ClientMusicPlayback;
import com.mengsama.mod.mengsamanetmusic.hud.MusicInfoHud;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import com.mengsama.mod.mengsamanetmusic.platform.PacketContext;

import java.util.function.Supplier;

 
public record PlayerHudVisibilityPacket(String targetId, boolean visible) {
    public static void encode(PlayerHudVisibilityPacket packet, FriendlyByteBuf buf) {
        buf.writeUtf(packet.targetId);
        buf.writeBoolean(packet.visible);
    }

    public static PlayerHudVisibilityPacket decode(FriendlyByteBuf buf) {
        return new PlayerHudVisibilityPacket(buf.readUtf(), buf.readBoolean());
    }

    public static void handle(PlayerHudVisibilityPacket packet, Supplier<PacketContext> contextSupplier) {
        ClientPacketDispatch.accept(contextSupplier, () -> handleClient(packet));
    }

    @OnlyIn(Dist.CLIENT)
    private static void handleClient(PlayerHudVisibilityPacket packet) {
        if (packet.visible()) return;
        var player = Minecraft.getInstance().player;
        if (player == null || !isOwnerTarget(packet.targetId(), player.getUUID())) {
            ClientMusicPlayback.stop(packet.targetId());
            MusicInfoHud.clearTarget(packet.targetId());
        }
    }

    static boolean isOwnerTarget(String targetId, java.util.UUID playerId) {
        return targetId != null && playerId != null && targetId.startsWith("item:" + playerId + ":");
    }
}

package com.mengsama.mod.mengsamanetmusic.network;

import com.mengsama.mod.mengsamanetmusic.client.audio.ClientMusicPlayback;
import net.minecraft.network.FriendlyByteBuf;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import com.mengsama.mod.mengsamanetmusic.platform.PacketContext;
import java.util.function.Supplier;

public record StopMusicPacketClient(String targetId) {
    public void encode(FriendlyByteBuf buf) { buf.writeUtf(targetId); }
    public static StopMusicPacketClient decode(FriendlyByteBuf buf) { return new StopMusicPacketClient(buf.readUtf()); }
    public static void handle(StopMusicPacketClient packet, Supplier<PacketContext> ctx) {
        ClientPacketDispatch.accept(ctx, () -> handleClient(packet));
    }
    @OnlyIn(Dist.CLIENT)
    private static void handleClient(StopMusicPacketClient packet) {
        if (packet.targetId.startsWith("device:")) {
            ClientMusicPlayback.stopPhysicalDevice(com.mengsama.mod.mengsamanetmusic.compat.PlaybackTargetId.instanceId(packet.targetId));
            return;
        }
        ClientMusicPlayback.stop(packet.targetId);
        com.mengsama.mod.mengsamanetmusic.earbuds.client.EarbudClient.forget(packet.targetId);
        com.mengsama.mod.mengsamanetmusic.hud.MusicInfoHud.onDeviceStopped(packet.targetId);
    }
}

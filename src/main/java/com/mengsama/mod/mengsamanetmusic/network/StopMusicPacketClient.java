package com.mengsama.mod.mengsamanetmusic.network;

import com.mengsama.mod.mengsamanetmusic.client.audio.ClientMusicPlayback;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

public record StopMusicPacketClient(String targetId) {
    public void encode(FriendlyByteBuf buf) { buf.writeUtf(targetId); }
    public static StopMusicPacketClient decode(FriendlyByteBuf buf) { return new StopMusicPacketClient(buf.readUtf()); }
    public static void handle(StopMusicPacketClient packet, Supplier<NetworkEvent.Context> ctx) {
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

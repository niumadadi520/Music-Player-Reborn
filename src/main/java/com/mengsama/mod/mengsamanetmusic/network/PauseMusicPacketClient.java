package com.mengsama.mod.mengsamanetmusic.network;

import com.mengsama.mod.mengsamanetmusic.client.audio.ClientMusicPlayback;
import net.minecraft.network.FriendlyByteBuf;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import com.mengsama.mod.mengsamanetmusic.platform.PacketContext;

import java.util.function.Supplier;

 
public record PauseMusicPacketClient(String targetId, boolean paused, long requestGeneration, long controlSequence) {
    private static final java.util.concurrent.atomic.AtomicLong CONTROL_SEQUENCES =
            new java.util.concurrent.atomic.AtomicLong(System.nanoTime());

    public PauseMusicPacketClient(String targetId, boolean paused) {
        this(targetId, paused, Long.MIN_VALUE, CONTROL_SEQUENCES.incrementAndGet());
    }

    public PauseMusicPacketClient(String targetId, boolean paused, long requestGeneration) {
        this(targetId, paused, requestGeneration, CONTROL_SEQUENCES.incrementAndGet());
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(targetId);
        buf.writeBoolean(paused);
        buf.writeLong(requestGeneration);
        buf.writeLong(controlSequence);
    }

    public static PauseMusicPacketClient decode(FriendlyByteBuf buf) {
        return new PauseMusicPacketClient(buf.readUtf(), buf.readBoolean(), buf.readLong(), buf.readLong());
    }

    public static void handle(PauseMusicPacketClient packet, Supplier<PacketContext> ctx) {
        ClientPacketDispatch.accept(ctx, () -> handleClient(packet));
    }

    @OnlyIn(Dist.CLIENT)
    private static void handleClient(PauseMusicPacketClient packet) {
        ClientMusicPlayback.setPaused(packet.targetId, packet.paused, packet.requestGeneration, packet.controlSequence);
    }
}

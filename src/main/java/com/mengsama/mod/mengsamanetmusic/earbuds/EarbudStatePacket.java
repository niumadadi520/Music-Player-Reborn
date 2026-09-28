package com.mengsama.mod.mengsamanetmusic.earbuds;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import com.mengsama.mod.mengsamanetmusic.platform.PacketContext;
import java.util.function.Supplier;
public record EarbudStatePacket(CompoundTag data) {
    public void encode(FriendlyByteBuf b) { b.writeNbt(data); }
    public static EarbudStatePacket decode(FriendlyByteBuf b) { CompoundTag data=b.readNbt(); return new EarbudStatePacket(data == null ? new CompoundTag() : data); }
    public static void handle(EarbudStatePacket p, Supplier<PacketContext> ctx) {
        var c=ctx.get(); c.enqueueWork(() -> com.mengsama.mod.mengsamanetmusic.earbuds.client.EarbudClient.receive(p.data)); 
    }
}

package com.mengsama.mod.mengsamanetmusic.earbuds;
import com.mengsama.mod.mengsamanetmusic.network.PlayerPlayMusicPacket;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;
 
public record EarbudAudioPacket(PlayerPlayMusicPacket play) {
    public void encode(FriendlyByteBuf b) { play.encode(b); }
    public static EarbudAudioPacket decode(FriendlyByteBuf b) { return new EarbudAudioPacket(PlayerPlayMusicPacket.decode(b)); }
    public static void handle(EarbudAudioPacket p, Supplier<NetworkEvent.Context> ctx) {
        var c=ctx.get(); c.enqueueWork(() -> com.mengsama.mod.mengsamanetmusic.earbuds.client.EarbudClient.play(p.play)); c.setPacketHandled(true);
    }
}

package com.mengsama.mod.mengsamanetmusic.network;
import java.util.function.Supplier;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;

 
public final class ClientPacketDispatch {
    public static void accept(Supplier<NetworkEvent.Context> supplier, Runnable action) {
        var delivery = supplier.get();
        delivery.setPacketHandled(true);
        if (delivery.getDirection() == NetworkDirection.PLAY_TO_CLIENT) delivery.enqueueWork(action);
    }
}

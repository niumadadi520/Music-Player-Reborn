package com.mengsama.mod.mengsamanetmusic.network;

import net.minecraft.network.FriendlyByteBuf;
import com.mengsama.mod.mengsamanetmusic.platform.PacketContext;

import java.util.function.Supplier;

public class SyncVipCookiePacket {
    private final boolean hasServerVipCookie;

    public static boolean CLIENT_HAS_VIP_COOKIE = false;

    public SyncVipCookiePacket(boolean hasServerVipCookie) {
        this.hasServerVipCookie = hasServerVipCookie;
    }

    public static SyncVipCookiePacket decode(FriendlyByteBuf buf) {
        return new SyncVipCookiePacket(buf.readBoolean());
    }

    public static void encode(SyncVipCookiePacket message, FriendlyByteBuf buf) {
        buf.writeBoolean(message.hasServerVipCookie);
    }

    public static void handle(SyncVipCookiePacket message, Supplier<PacketContext> contextSupplier) {
        ClientPacketDispatch.accept(contextSupplier, () -> CLIENT_HAS_VIP_COOKIE = message.hasServerVipCookie);
    }

    public boolean hasServerVipCookie() {
        return hasServerVipCookie;
    }
}

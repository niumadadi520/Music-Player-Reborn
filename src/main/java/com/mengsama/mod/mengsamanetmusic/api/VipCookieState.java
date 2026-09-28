package com.mengsama.mod.mengsamanetmusic.api;
import com.mengsama.mod.mengsamanetmusic.config.ConfigManager;
import com.mengsama.mod.mengsamanetmusic.network.SyncVipCookiePacket;

 
public final class VipCookieState {
    private VipCookieState(){}
    private static String available(){
        String session=QqCredentialManager.getEffectiveCookie();
        if(!session.isBlank())return session;
        return java.util.Objects.requireNonNullElse(ConfigManager.getQqCookie(),"");
    }
    public static String getClientEffectiveVipCookie(){return available();}
    public static String getServerEffectiveVipCookie(){return available();}
    public static String getEffectiveVipCookie(){return available();}
    public static boolean hasServerVipCookieAvailable(){return !available().isBlank();}
    public static boolean canSkipVipCookieWarningOnClient(){return !available().isBlank()||SyncVipCookiePacket.CLIENT_HAS_VIP_COOKIE;}
}

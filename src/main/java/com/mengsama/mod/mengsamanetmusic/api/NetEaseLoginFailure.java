package com.mengsama.mod.mengsamanetmusic.api;

import java.io.IOException;

 
public final class NetEaseLoginFailure extends IOException {
    public enum Stage {
        CREATE("申请二维码"), POLL("读取手机授权"), COOKIE("领取登录凭据"), ACCOUNT("验证网易云账号"), IMAGE("绘制二维码");
        final String label;
        Stage(String label) { this.label = label; }
    }
    public enum Reason { TRANSPORT, HTTP, JSON, SERVICE, MISSING_KEY, MISSING_COOKIE, INVALID_COOKIE, NO_ACCOUNT, IMAGE }
    private final long serviceCode;
    public NetEaseLoginFailure(Stage stage, Reason reason, int http, long code) {
        super(stage.label + "失败 [" + stage + "/" + reason + "; HTTP=" + http + "; code=" + code + "]" +
                (code==8821 ? "，网易云要求行为验证，请使用官方网页登录" : "，请刷新重试"));
        this.serviceCode=code;
    }
    public static boolean requiresVerification(Throwable error) {
        for(int depth=0;error!=null && depth<8;depth++,error=error.getCause())
            if(error instanceof NetEaseLoginFailure known)return known.serviceCode==8821;
        return false;
    }
    public static String shortStatus(Throwable error) {
        for(int depth=0;error!=null && depth<8;depth++,error=error.getCause())
            if(error instanceof NetEaseLoginFailure known)
                return known.serviceCode==8821 ? "8821：需要安全验证" : known.serviceCode!=0 ? known.serviceCode+"：登录失败" : "登录失败，悬停查看详情";
        return "登录失败，悬停查看详情";
    }
    public static String describe(Throwable error, Stage fallback) {
        for (int depth=0; error!=null && depth<8; depth++,error=error.getCause())
            if (error instanceof NetEaseLoginFailure known) return known.getMessage();
        return new NetEaseLoginFailure(fallback, Reason.TRANSPORT, 0, 0).getMessage();
    }
}

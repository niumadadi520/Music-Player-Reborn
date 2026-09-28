package com.mengsama.mod.mengsamanetmusic.gui;

import com.mengsama.mod.mengsamanetmusic.api.QqCredentialManager;
import com.mengsama.mod.mengsamanetmusic.api.QqLoginService;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

 
interface QqLoginBackend {
    CompletableFuture<byte[]> fetchQrCode();
    CompletableFuture<QqLoginService.LoginState> pollLogin();
    void setStateListener(Consumer<QqLoginService.LoginState> listener);
    void reset();
    boolean hasCredential();
    void logout();
    String safeSummary();
    String failureDetail();
    default long generation() { return 0; }
    default boolean isCurrent(long generation) { return true; }
    QqLoginBackend LIVE = new QqLoginBackend() {
        public CompletableFuture<byte[]> fetchQrCode() { return QqLoginService.fetchQrCode(); }
        public CompletableFuture<QqLoginService.LoginState> pollLogin() { return QqLoginService.pollLogin(); }
        public void setStateListener(Consumer<QqLoginService.LoginState> listener) { QqLoginService.QrSession.setStateListener(listener); }
        public void reset() { QqLoginService.QrSession.reset(); }
        public boolean hasCredential() { return QqCredentialManager.hasValidCredential(); }
        public void logout() { QqCredentialManager.clear(); }
        public String safeSummary() { return QqLoginService.QrSession.getSafeSummary(); }
        public String failureDetail() {
            var error=QqLoginService.QrSession.getLastError();
            String message=switch(error){
                case QR_REQUEST_FAILED,QR_COOKIE_MISSING -> "二维码申请失败，请刷新";
                case POLL_REQUEST_FAILED,INVALID_CALLBACK -> "扫码状态校验失败，请重试";
                case VERIFICATION_REQUEST_FAILED,SESSION_COOKIE_MISSING -> "手机确认后的登录会话校验失败";
                case OAUTH_REQUEST_FAILED,OAUTH_CODE_MISSING -> "QQ 授权码获取失败，请重新扫码";
                case MUSIC_LOGIN_FAILED,INVALID_CREDENTIAL -> "QQ 音乐账号登录失败，请重新扫码";
                default -> "";
            };
            return message+" ("+error.name()+")";
        }
        public long generation() { return QqLoginService.QrSession.generation(); }
        public boolean isCurrent(long generation) { return QqLoginService.QrSession.isCurrent(generation); }
    };
}

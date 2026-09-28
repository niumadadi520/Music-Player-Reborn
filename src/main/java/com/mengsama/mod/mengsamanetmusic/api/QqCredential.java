package com.mengsama.mod.mengsamanetmusic.api;

import com.google.gson.annotations.SerializedName;
import java.time.Instant;
import java.util.Objects;

 


public record QqCredential(@SerializedName("musicid") String account, @SerializedName("musickey") String accessKey, @SerializedName("keyExpiresIn") long lifetime, @SerializedName("musickeyCreateTime") long issuedAt, @SerializedName("refresh_key") String renewalKey, @SerializedName("refresh_token") String renewalToken) {

    public QqCredential {
        account = clean(account);
        accessKey = clean(accessKey);
        renewalKey = clean(renewalKey);
        renewalToken = clean(renewalToken);
        lifetime = Math.max(0, lifetime);
        issuedAt = Math.max(0, issuedAt);
    }

    public QqCredential() {
        this("", "", 0, 0, "", "");
    }

    private static String clean(String text) {
        return Objects.requireNonNullElse(text, "").strip();
    }

    boolean isExpiredAt(Instant time) {
        return lifetime > 0 && issuedAt > 0 && time.getEpochSecond() >= issuedAt && time.getEpochSecond() - issuedAt >= lifetime;
    }

    public boolean isExpired() {
        return isExpiredAt(Instant.now());
    }

    public boolean isValid() {
        return safeCookieValue(account) && safeCookieValue(accessKey) && !isExpired();
    }

    private static boolean safeCookieValue(String value) {
        return !value.isEmpty() && value.chars().noneMatch(c -> c == ';' || c < 32 || c == 127);
    }

    public String toCookieString() {
        return isValid() ? "uin=" + account + "; qm_keyst=" + accessKey : "";
    }

    public String getMusicId() {
        return account;
    }

    public String getMusicKey() {
        return accessKey;
    }

    public long getKeyExpiresIn() {
        return lifetime;
    }

    public long getMusicKeyCreateTime() {
        return issuedAt;
    }

    public String getRefreshKey() {
        return renewalKey;
    }

    public String getRefreshToken() {
        return renewalToken;
    }

    @Override
    public String toString() {
        return "QQ credential (redacted)";
    }
}

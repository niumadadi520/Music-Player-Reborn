package com.mengsama.mod.mengsamanetmusic.api;

import com.mengsama.mod.mengsamanetmusic.api.qq.QqCredentialFile;
import com.mengsama.mod.mengsamanetmusic.MengSamaNetMusic;
import java.nio.file.Path;
import java.io.IOException;

 


public final class QqCredentialManager {

    private record Snapshot(QqCredential credential, long revision) {
    }

    private static volatile Snapshot current = new Snapshot(null, 0);

    private static QqCredentialFile storage;

    private QqCredentialManager() {
    }

    public static synchronized void init(Path root) {
        storage = new QqCredentialFile(root.resolve("mengsamanetmusic/credential.json"));
        load();
    }

    public static synchronized void load() {
        try {
            publish(storage == null ? null : storage.read());
        } catch (IOException failure) {
            MengSamaNetMusic.LOGGER.warn("QQ saved session was unreadable; file retained");
            publish(null);
        }
    }

    public static synchronized void save(QqCredential value) {
         
        if (value == null || !value.isValid())
            return;
        try {
            if (storage != null)
                storage.write(value);
        } catch (IOException failure) {
            MengSamaNetMusic.LOGGER.warn("QQ session is usable in memory but could not be saved");
        }
        publish(value);
    }

    public static synchronized void clear() {
        try {
            if (storage != null)
                storage.clear();
        } catch (IOException failure) {
            MengSamaNetMusic.LOGGER.warn("QQ logout could not update the saved session file");
        }
        publish(null);
    }

    private static void publish(QqCredential value) {
        current = new Snapshot(value != null && value.isValid() ? value : null, current.revision() + 1);
    }

    public static QqCredential getCredential() {
        return current.credential();
    }

    public static long revision() {
        return current.revision();
    }

    public static boolean hasValidCredential() {
        var value = getCredential();
        return value != null && value.isValid();
    }

    public static String getEffectiveCookie() {
        var value = getCredential();
        return value == null ? "" : value.toCookieString();
    }

    public static String getMusicId() {
        var value = getCredential();
        return value == null ? "" : value.getMusicId();
    }
}

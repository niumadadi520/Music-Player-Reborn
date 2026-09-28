package com.mengsama.mod.mengsamanetmusic.api.qq;

import com.google.gson.*;
import com.mengsama.mod.mengsamanetmusic.api.QqCredential;
import java.io.IOException;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;

 


public final class QqCredentialFile {

    private final Path file;

    private JsonObject envelope = new JsonObject();

    public QqCredentialFile(Path path) {
        file = path;
    }

    public synchronized QqCredential read() throws IOException {
        if (!Files.isRegularFile(file))
            return null;
        if (Files.size(file) > 65536)
            throw new IOException("QQ credential file too large");
        JsonObject root = QqFields.parse(Files.readString(file, StandardCharsets.UTF_8));
        envelope = root.deepCopy();
        return decode(root, 0);
    }

    public synchronized void write(QqCredential value) throws IOException {
        if (value == null || !value.isValid())
            throw new IOException("Refusing invalid QQ credential replacement");
        JsonObject update = envelope.deepCopy();
        JsonObject encoded = QqFields.properties("musicid", value.getMusicId(), "musickey", value.getMusicKey(), "keyExpiresIn", value.getKeyExpiresIn(), "musickeyCreateTime", value.getMusicKeyCreateTime(), "refresh_key", value.getRefreshKey(), "refresh_token", value.getRefreshToken());
        encoded.entrySet().forEach(entry -> update.add(entry.getKey(), entry.getValue()));
        commit(update);
        envelope = update;
    }

    public synchronized void clear() throws IOException {
        commit(new JsonObject());
        envelope = new JsonObject();
    }

    private void commit(JsonObject value) throws IOException {
        Files.createDirectories(file.getParent());
        Path staging = Files.createTempFile(file.getParent(), "qq-session-", ".tmp");
        try {
            Files.writeString(staging, value.toString(), StandardCharsets.UTF_8);
            try {
                Files.move(staging, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(staging, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(staging);
        }
    }

    public static QqCredential decode(JsonObject root, long issuedFallback) {
        return new QqCredential(QqFields.text(root, "musicid", "musicId", "str_musicid"), QqFields.text(root, "musickey", "musicKey", "music_key"), QqFields.number(root, "keyExpiresIn", 0), QqFields.number(root, "musickeyCreateTime", issuedFallback), QqFields.text(root, "refresh_key", "refreshKey"), QqFields.text(root, "refresh_token", "refreshToken"));
    }
}

package com.mengsama.mod.mengsamanetmusic.cache;

import com.google.gson.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

 
final class CacheIndexStore {
    private final Path directory;
    private JsonObject document;
    private final String filename;

    CacheIndexStore(Path directory) { this(directory, "index.json"); }
    CacheIndexStore(Path directory, String filename) {
        this.directory = directory.toAbsolutePath().normalize();
        if (!filename.matches("[A-Za-z0-9_-]+\\.json")) throw new IllegalArgumentException("Invalid index filename");
        this.filename = filename;
    }

    synchronized Map<String, String> read() throws IOException {
        if (document == null) {
            Files.createDirectories(directory);
            Path index = directory.resolve(filename);
            if (Files.exists(index)) {
                try {
                    if (Files.size(index) > 16L * 1024 * 1024) throw new IOException("Cache index size limit");
                    document = JsonParser.parseString(Files.readString(index)).getAsJsonObject();
                } catch (IOException | RuntimeException unreadable) {
                     
                    Path backup = Files.createTempFile(directory, "index-unreadable-", ".json");
                    Files.copy(index, backup, StandardCopyOption.REPLACE_EXISTING);
                    document = new JsonObject();
                }
            } else document = new JsonObject();
        }
        Map<String, String> entries = new HashMap<>();
        document.entrySet().forEach(entry -> {
            JsonElement value = entry.getValue();
            if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString() && safeName(value.getAsString()))
                entries.put(entry.getKey(), value.getAsString());
        });
        return entries;
    }

    synchronized void write(Map<String, String> entries) throws IOException {
        read();
        JsonObject updated = document.deepCopy();
        for (var entry : entries.entrySet()) {
            if (!safeName(entry.getValue())) throw new IOException("Unsafe cache file identity");
            updated.addProperty(entry.getKey(), entry.getValue());
        }
        Path index = directory.resolve(filename);
        Path temporary = Files.createTempFile(directory, "index-write-", ".tmp");
        try {
            Files.writeString(temporary, updated.toString());
            if (Files.exists(index)) Files.copy(index, directory.resolve(filename + ".bak"), StandardCopyOption.REPLACE_EXISTING);
            try { Files.move(temporary, index, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException unsupported) { Files.move(temporary, index, StandardCopyOption.REPLACE_EXISTING); }
            document = updated;
        } finally { Files.deleteIfExists(temporary); }
    }

    static boolean safeName(String value) { return value != null && value.matches("[A-Za-z0-9_-]+"); }
}

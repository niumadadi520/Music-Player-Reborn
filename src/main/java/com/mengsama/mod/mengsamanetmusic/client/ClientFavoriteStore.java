package com.mengsama.mod.mengsamanetmusic.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mengsama.mod.mengsamanetmusic.api.SongInfo;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

 
public final class ClientFavoriteStore {
    static final int MAX_FAVORITES = 2_000;
    static final int MAX_FILE_BYTES = 8 * 1024 * 1024;
    private static final int MAX_TEXT_LENGTH = 16_384;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Result UNAVAILABLE = new Result(false, false, "收藏目录尚未就绪，请进入游戏后重试");
    private static volatile Store instance;

    public record Result(boolean success, boolean favorite, String message) {}

    private ClientFavoriteStore() {}

     
    public static List<SongInfo> snapshot() {
        Store store = store();
        return store == null ? List.of() : store.snapshot();
    }

    public static boolean contains(SongInfo song) {
        Store store = instance;  
        return store != null && store.contains(song);
    }

    public static boolean isLoaded() { return instance != null; }

     
    public static Result toggle(SongInfo song) {
        Store store = store();
        return store == null ? UNAVAILABLE : store.toggle(song);
    }

     
    public static Result status() {
        Store store = instance;
        return store == null ? UNAVAILABLE : store.status();
    }

    private static synchronized Store store() {
        if (instance == null) {
            try {
                Path config = FMLPaths.CONFIGDIR.get();
                if (config == null) return null;
                instance = new Store(config.resolve("mengsamanetmusic").resolve("favorites.json"));
            } catch (RuntimeException unavailable) {
                return null;  
            }
        }
        return instance;
    }

     
    static final class Store {
        private final Path file;
        private volatile Map<String, Entry> favorites = Map.of();
        private JsonObject envelope = new JsonObject();
        private byte[] loadedBytes;
        private boolean readable;
        private volatile Result lastResult = new Result(true, false, "");

        Store(Path file) {
            this.file = file.toAbsolutePath().normalize();
            load();
        }

        List<SongInfo> snapshot() {
            return favorites.values().stream().map(entry -> entry.song.clone()).toList();
        }

        boolean contains(SongInfo song) {
            return favorites.containsKey(identity(song));
        }

        Result status() { return lastResult; }

        synchronized Result toggle(SongInfo input) {
            String key = identity(input);
            boolean wasFavorite = favorites.containsKey(key);
            if (!readable) return lastResult;
            Map<String, Entry> updated = new LinkedHashMap<>(favorites);
            try {
                if (wasFavorite) {
                    updated.remove(key);
                } else {
                    if (updated.size() >= MAX_FAVORITES) {
                        return fail(false, "收藏已达到 " + MAX_FAVORITES + " 首上限，请先取消部分收藏");
                    }
                    SongInfo song = prepare(input);
                    updated.put(song.identityKey(), new Entry(song, GSON.toJsonTree(song).getAsJsonObject()));
                }
                JsonObject document = envelope.deepCopy();
                document.addProperty("version", 1);
                JsonArray songs = new JsonArray();
                updated.values().forEach(entry -> songs.add(entry.json.deepCopy()));
                document.add("songs", songs);
                byte[] encoded = GSON.toJson(document).getBytes(StandardCharsets.UTF_8);
                if (encoded.length > MAX_FILE_BYTES) return fail(wasFavorite, "收藏文件超过 8 MiB 上限，原有收藏未改动");
                if (!Arrays.equals(loadedBytes, readBounded(file))) {
                    return fail(wasFavorite, "收藏文件已被其他程序修改，请重启游戏后重试；原文件未改动");
                }
                commit(encoded);
                favorites = Collections.unmodifiableMap(new LinkedHashMap<>(updated));
                envelope = document;
                loadedBytes = encoded;
                return lastResult = new Result(true, !wasFavorite, wasFavorite ? "已取消收藏" : "已加入收藏");
            } catch (IllegalArgumentException invalid) {
                return fail(wasFavorite, "无法收藏：" + invalid.getMessage());
            } catch (IOException | RuntimeException failure) {
                return fail(wasFavorite, "收藏保存失败，原有收藏未改动（请检查目录权限或磁盘空间）");
            }
        }

        private Result fail(boolean favorite, String message) {
            return lastResult = new Result(false, favorite, message);
        }

        private void load() {
            try {
                loadedBytes = readBounded(file);
                if (loadedBytes == null) {
                    readable = true;
                    return;  
                }
                String text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(loadedBytes)).toString();
                JsonElement parsed = JsonParser.parseString(text);
                if (!parsed.isJsonObject()) throw new IllegalArgumentException("invalid root");
                JsonObject document = parsed.getAsJsonObject();
                if (!document.has("version") || !document.get("version").isJsonPrimitive()
                        || !document.getAsJsonPrimitive("version").isNumber()
                        || !"1".equals(document.get("version").getAsString())
                        || !document.has("songs") || !document.get("songs").isJsonArray()) {
                    throw new IllegalArgumentException("unsupported format");
                }
                JsonArray songs = document.getAsJsonArray("songs");
                if (songs.size() > MAX_FAVORITES) throw new IllegalArgumentException("too many entries");
                Map<String, Entry> loaded = new LinkedHashMap<>();
                for (JsonElement item : songs) {
                    if (!item.isJsonObject()) throw new IllegalArgumentException("invalid song");
                    SongInfo song = prepare(GSON.fromJson(item, SongInfo.class));
                     
                    JsonObject preserved = item.getAsJsonObject().deepCopy();
                    GSON.toJsonTree(song).getAsJsonObject().entrySet().forEach(field -> preserved.add(field.getKey(), field.getValue()));
                    preserved.remove("resolvedMediaUrl");
                    preserved.remove("playbackHeaders");
                    loaded.putIfAbsent(song.identityKey(), new Entry(song, preserved));
                }
                favorites = Collections.unmodifiableMap(new LinkedHashMap<>(loaded));
                envelope = document;
                readable = true;
            } catch (IOException | RuntimeException malformed) {
                favorites = Map.of();
                readable = false;
                fail(false, "收藏文件无法读取、格式损坏或超过上限，已保留原文件并暂停写入；请修复 favorites.json 后重启游戏");
            }
        }

        private void commit(byte[] encoded) throws IOException {
            Files.createDirectories(file.getParent());
            Path temporary = Files.createTempFile(file.getParent(), ".favorites-", ".tmp");
            try {
                try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
                    ByteBuffer buffer = ByteBuffer.wrap(encoded);
                    while (buffer.hasRemaining()) channel.write(buffer);
                    channel.force(true);
                }
                 
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } finally {
                try { Files.deleteIfExists(temporary); } catch (IOException cleanupFailure) {
                     
                }
            }
        }
    }

    private record Entry(SongInfo song, JsonObject json) {}

    private static byte[] readBounded(Path file) throws IOException {
        if (Files.notExists(file)) return null;
        try (InputStream input = Files.newInputStream(file)) {
            byte[] bytes = input.readNBytes(MAX_FILE_BYTES + 1);
            if (bytes.length > MAX_FILE_BYTES) throw new IOException("favorites file too large");
            return bytes;
        }
    }

    private static String identity(SongInfo song) {
        if (song == null) return "";
        try { return song.clone().identityKey(); }
        catch (RuntimeException invalid) { return ""; }
    }

    private static SongInfo prepare(SongInfo input) {
        if (input == null) throw new IllegalArgumentException("没有可收藏的歌曲");
        for (String text : new String[] {input.songUrl, input.songName, input.transName, input.source,
                input.providerId, input.rawUrl, input.picUrl, input.albumMid, input.coverUrl, input.albumName}) {
            if (text != null && text.length() > MAX_TEXT_LENGTH) throw new IllegalArgumentException("歌曲信息过长");
        }
        if (input.artists != null) {
            if (input.artists.size() > 128) throw new IllegalArgumentException("歌手信息过多");
            for (String artist : input.artists) {
                if (artist == null || artist.length() > MAX_TEXT_LENGTH) throw new IllegalArgumentException("歌手信息无效");
            }
        }
        SongInfo song = input.clone();
        song.normalizeIdentity();
        if (song.songName == null || song.songName.isBlank()) throw new IllegalArgumentException("歌曲名称为空");
        if (song.songTime < 0) throw new IllegalArgumentException("歌曲时长无效");
        if (song.identityKey().isBlank() || !List.of("netease", "qq", "apple").contains(song.source)) {
            throw new IllegalArgumentException("歌曲缺少稳定的音乐平台标识");
        }
        URI original = URI.create(song.rawUrl);
        if (!("https".equalsIgnoreCase(original.getScheme()) || "http".equalsIgnoreCase(original.getScheme()))
                || original.getHost() == null) throw new IllegalArgumentException("歌曲原始链接无效");
        if (song.songUrl == null || song.songUrl.isBlank() || SongInfo.isLikelyTemporaryUrl(song.songUrl)) song.songUrl = song.rawUrl;
        song.resolvedMediaUrl = "";
        song.playbackHeaders.clear();
        return song;
    }
}

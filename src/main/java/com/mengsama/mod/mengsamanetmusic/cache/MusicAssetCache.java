package com.mengsama.mod.mengsamanetmusic.cache;

import com.google.gson.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import net.minecraftforge.fml.loading.FMLPaths;

 
public final class MusicAssetCache {
    private static final class ClientInstance {
        static final MusicAssetCache VALUE = new MusicAssetCache(
                Optional.ofNullable(FMLPaths.CONFIGDIR.get()).orElse(Path.of("config")).resolve("netMusicListCache"));
    }
    public static MusicAssetCache instance() { return ClientInstance.VALUE; }
    private final Path directory;
    private final CacheIndexStore covers;
    private final ExecutorService files = Executors.newSingleThreadExecutor(job -> daemon(job, "MengSama-Cover-Index"));
    private final ExecutorService downloads = new ThreadPoolExecutor(4, 4, 30, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(128), job -> daemon(job, "MengSama-Cover-Download"), new ThreadPoolExecutor.AbortPolicy());
    private final Map<Long, CompletableFuture<Path>> pending = new HashMap<>();
    private volatile Map<String, String> entries = Map.of(), legacy = Map.of();
    private CompletableFuture<Void> loading;

    public MusicAssetCache(Path directory) {
        this.directory = directory.toAbsolutePath().normalize();
        covers = new CacheIndexStore(this.directory, "covers.json");
    }
    private static Thread daemon(Runnable job, String name) {
        Thread thread = new Thread(job, name); thread.setDaemon(true); return thread;
    }
    public synchronized CompletableFuture<Void> loadAsync() {
        if (loading == null || loading.isCompletedExceptionally()) {
            loading = CompletableFuture.runAsync(() -> {
                try { entries = Map.copyOf(covers.read()); legacy = readLegacy(); }
                catch (IOException failure) { throw new CompletionException(failure); }
            }, files);
        }
        return loading;
    }
    private Map<String, String> readLegacy() {
        Path old = directory.resolve("index.json");
        try {
            if (!Files.isRegularFile(old) || Files.size(old) > 16L * 1024 * 1024) return Map.of();
            JsonObject document = JsonParser.parseString(Files.readString(old)).getAsJsonObject();
            Map<String, String> result = new HashMap<>();
            document.entrySet().forEach(entry -> {
                JsonElement value = entry.getValue();
                if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()
                        && CacheIndexStore.safeName(value.getAsString())) result.put(entry.getKey(), value.getAsString());
            });
            return Map.copyOf(result);
        } catch (IOException | RuntimeException unreadable) {
             
            return Map.of();
        }
    }
    public synchronized CompletableFuture<Path> requestCover(long song, Supplier<String> source) {
        if (song <= 0 || source == null) return CompletableFuture.failedFuture(new IllegalArgumentException("Invalid cover request"));
        CompletableFuture<Path> existing = pending.get(song);
        if (existing != null) return existing;
        CompletableFuture<Path> result = new CompletableFuture<>();
        pending.put(song, result);
        CompletableFuture<Void> ready = loadAsync();
        try { downloads.execute(() -> transfer(song, source, ready, result)); }
        catch (RejectedExecutionException full) { pending.remove(song); result.completeExceptionally(full); }
        return result;
    }
    private void transfer(long song, Supplier<String> source, CompletableFuture<Void> ready, CompletableFuture<Path> result) {
        try {
            ready.join();
            String id = Long.toString(song), name = entries.get(id);
            Path image = existing(name);
            if (image == null) {
                name = legacy.get(id); image = existing(name);
                if (image == null) {
                     
                    String stem = "cover-" + id;
                    name = stem; image = directory.resolve(name + ".png");
                    int suffix = 0;
                    while (Files.exists(image) && existing(name) == null) {
                         
                        name = stem + "-" + (++suffix); image = directory.resolve(name + ".png");
                    }
                    if (!Files.isRegularFile(image)) CacheTransfer.fetch(source.get(), image);
                }
                final String savedName = name;
                CompletableFuture.runAsync(() -> {
                    try {
                        covers.write(Map.of(id, savedName));
                        Map<String, String> updated = new HashMap<>(entries);
                        updated.put(id, savedName); entries = Map.copyOf(updated);
                    } catch (IOException failure) { throw new CompletionException(failure); }
                }, files).join();
            }
            result.complete(image);
        } catch (Exception failure) { result.completeExceptionally(failure); }
        finally { synchronized (this) { pending.remove(song, result); } }
    }
    private Path existing(String name) {
        if (!CacheIndexStore.safeName(name)) return null;
        Path file = directory.resolve(name + ".png");
        if (!Files.isRegularFile(file)) return null;
        try { CacheTransfer.validateImage(file); return file; }
        catch (IOException | RuntimeException invalid) { return null; }
    }
    synchronized int activeDownloads() { return pending.size(); }
    public void shutdown() { downloads.shutdown(); files.shutdown(); }
}

package com.mengsama.mod.mengsamanetmusic.api;

import com.mengsama.mod.mengsamanetmusic.MengSamaNetMusic;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

 
public final class NetEaseSearchMetadataLoader {
    static final long FAILURE_RETRY_MS = 15_000L;
    private static final Map<Long, SongInfo> CACHE = new ConcurrentHashMap<>();
    private static final Map<Long, CompletableFuture<SongInfo>> IN_FLIGHT = new ConcurrentHashMap<>();
    private static final Map<Long, Long> RETRY_AFTER = new ConcurrentHashMap<>();
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(4, new DetailThreadFactory());

    private NetEaseSearchMetadataLoader() {}

    public static CompletableFuture<List<NetEaseSearchResult>> hydrateMissing(
            List<NetEaseSearchResult> rows, NetEaseApi api) {
        return hydrateMissing(rows, id -> {
            Map<Long, SongInfo> details = api.searchDetails(new long[]{id});
            return details.get(id);
        }, EXECUTOR, System.currentTimeMillis());
    }

    static CompletableFuture<List<NetEaseSearchResult>> hydrateMissing(
            List<NetEaseSearchResult> rows, DetailFetcher fetcher, Executor executor, long now) {
        if (rows == null || rows.isEmpty()) return CompletableFuture.completedFuture(List.of());
        List<CompletableFuture<NetEaseSearchResult>> pending = new ArrayList<>(rows.size());
        for (NetEaseSearchResult row : rows) {
            if (row == null || row.isQq() || row.isApple() || !row.getCoverUrl().isBlank()) {
                pending.add(CompletableFuture.completedFuture(row));
                continue;
            }
            long id;
            try { id = Long.parseLong(row.getSongId()); }
            catch (NumberFormatException ignored) {
                pending.add(CompletableFuture.completedFuture(row));
                continue;
            }
            SongInfo cached = CACHE.get(id);
            if (cached != null) {
                pending.add(CompletableFuture.completedFuture(row.mergeDetail(cached)));
                continue;
            }
            Long retryAt = RETRY_AFTER.get(id);
            if (retryAt != null && now < retryAt) {
                pending.add(CompletableFuture.completedFuture(row));
                continue;
            }
            RETRY_AFTER.remove(id);
            CompletableFuture<SongInfo> detail = load(id, fetcher, executor);
            pending.add(detail.handle((value, error) -> error == null ? row.mergeDetail(value) : row));
        }
        return CompletableFuture.allOf(pending.toArray(CompletableFuture[]::new))
                .thenApply(ignored -> pending.stream().map(CompletableFuture::join).toList());
    }

    private static CompletableFuture<SongInfo> load(long id, DetailFetcher fetcher, Executor executor) {
        CompletableFuture<SongInfo> existing = IN_FLIGHT.get(id);
        if (existing != null) return existing;
        CompletableFuture<SongInfo> created = new CompletableFuture<>();
        existing = IN_FLIGHT.putIfAbsent(id, created);
        if (existing != null) return existing;
        CompletableFuture.supplyAsync(() -> fetch(fetcher, id), executor).whenComplete((value, error) -> {
            try {
                if (error == null && value != null && !value.preferredCoverUrl().isBlank()) {
                    CACHE.put(id, value.clone());
                    RETRY_AFTER.remove(id);
                } else {
                    RETRY_AFTER.put(id, System.currentTimeMillis() + FAILURE_RETRY_MS);
                }
                if (error == null) created.complete(value); else created.completeExceptionally(error);
            } finally {
                IN_FLIGHT.remove(id, created);
            }
        });
        return created;
    }

    private static SongInfo fetch(DetailFetcher fetcher, long id) {
        try { return fetcher.fetch(id); }
        catch (Exception error) {
            MengSamaNetMusic.LOGGER.debug("NetEase search detail failed for id {}", id, error);
            return null;
        }
    }

    @FunctionalInterface
    interface DetailFetcher { SongInfo fetch(long id) throws Exception; }

    static void clearForTests() {
        CACHE.clear();
        IN_FLIGHT.clear();
        RETRY_AFTER.clear();
    }

    private static final class DetailThreadFactory implements ThreadFactory {
        private int sequence;
        @Override public synchronized Thread newThread(Runnable task) {
            Thread thread = new Thread(task, "NetEase-Search-Detail-" + (++sequence));
            thread.setDaemon(true);
            return thread;
        }
    }
}

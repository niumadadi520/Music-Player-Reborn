package com.mengsama.mod.mengsamanetmusic.api;

import com.mengsama.mod.mengsamanetmusic.util.AsyncIoExecutor;
import com.mengsama.mod.mengsamanetmusic.api.qq.QqTrack;
import java.util.*;
import java.util.concurrent.*;

 


public final class QqMusicApi {

    @FunctionalInterface
    private interface Operation<T> {

        T run() throws Exception;
    }

    private static <T> CompletableFuture<T> submit(Operation<T> task) {
        return AsyncIoExecutor.supplyAsync(() -> {
            try {
                return task.run();
            } catch (Exception failure) {
                throw new CompletionException(failure);
            }
        });
    }

    public static CompletableFuture<List<SongInfo>> search(String query) {
        return submit(() -> QqMusicUtils.search(query).stream().map(QqMusicApi::fromSearch).toList());
    }





    public static CompletableFuture<SongInfo> fetchSongDetail(String mid) {
        return submit(() -> QqMusicUtils.CATALOG.detail(mid));
    }

    private static SongInfo fromSearch(QqSearchResult row) {
        return new QqTrack(row.getId(), row.getId(), 0, row.getTitle(), row.getDuration(), row.getSinger().isBlank() ? List.of() : List.of(row.getSinger()), row.getAlbumMid(), row.getAlbumPmId(), row.getAlbumName(), row.isVip()).song();
    }
}

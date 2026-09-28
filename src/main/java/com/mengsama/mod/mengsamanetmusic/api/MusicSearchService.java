package com.mengsama.mod.mengsamanetmusic.api;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import com.mengsama.mod.mengsamanetmusic.MengSamaNetMusic;
import com.mengsama.mod.mengsamanetmusic.util.AsyncIoExecutor;

 
public final class MusicSearchService {
    public static CompletableFuture<List<NetEaseSearchResult>> search(int provider, String query) {
        return AsyncIoExecutor.supplyAsync(() -> {
            try {
                return switch (provider) {
                    case 1 -> QqMusicUtils.search(query).stream().map(MusicSearchService::qqRow).toList();
                    case 2 -> AppleMusicApi.search(query);
                    default -> NetEaseApi.parseSearchResults(MengSamaNetMusic.NET_EASE_API.search(query, 1, 30));
                };
            } catch (Exception failure) { throw new java.util.concurrent.CompletionException(failure); }
        });
    }
    private static NetEaseSearchResult qqRow(QqSearchResult song) {
        return new NetEaseSearchResult(song.getId(), song.getTitle(), song.getSinger(), song.isVip(),
                "qq", song.getAlbumMid(), song.getCoverUrl(), song.getAlbumName(), song.getDuration());
    }
}

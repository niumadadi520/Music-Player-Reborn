package com.mengsama.mod.mengsamanetmusic.gui;

import com.mengsama.mod.mengsamanetmusic.api.AppleMusicApi;
import com.mengsama.mod.mengsamanetmusic.api.NetEaseSearchResult;
import com.mengsama.mod.mengsamanetmusic.api.SongInfo;
import com.mengsama.mod.mengsamanetmusic.client.ClientFavoriteStore;
import com.mengsama.mod.mengsamanetmusic.util.AsyncIoExecutor;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

 
final class SongFavoriteActions {
    private SongFavoriteActions() {}

    static SongInfo fromSearchResult(NetEaseSearchResult row) {
        if (row == null) return null;
        if (row.isApple()) return AppleMusicApi.toSong(row);
        String url = row.isQq() ? "https://y.qq.com/n/ryqq/songDetail/" + row.getSongId()
                : "https://music.163.com/song?id=" + row.getSongId();
        SongInfo song = new SongInfo(url, row.getSongName(), row.getDuration(), row.isVip());
        song.source = row.getSource();
        song.providerId = row.getSongId();
        song.rawUrl = url;
        song.albumMid = row.getAlbumMid();
        song.albumName = row.getAlbumName();
        song.picUrl = row.getCoverUrl();
        song.coverUrl = row.getCoverUrl();
        if (row.getArtistName() != null && !row.getArtistName().isBlank()) song.artists.add(row.getArtistName());
        song.normalizeIdentity();
        return song;
    }

    static NetEaseSearchResult toSearchResult(SongInfo song) {
        return new NetEaseSearchResult(song.providerId, song.songName,
                song.artists == null ? "" : String.join(" / ", song.artists), song.vip, song.source,
                "apple".equals(song.source) ? song.songUrl : song.albumMid,
                song.preferredCoverUrl(), song.albumName, song.songTime);
    }

    static void toggle(SongInfo song, Consumer<Component> feedback, Runnable refresh) {
        if (song == null) {
            feedback.accept(Component.literal("这首歌暂时没有可收藏的歌曲信息"));
            return;
        }
        SongInfo captured = song.clone();
        AsyncIoExecutor.supplyAsync(() -> ClientFavoriteStore.toggle(captured)).whenComplete((result, error) ->
                Minecraft.getInstance().execute(() -> {
                    feedback.accept(Component.literal(error == null ? result.message() : "收藏保存失败，请稍后重试"));
                    if (error == null && result.success()) refresh.run();
                }));
    }
}

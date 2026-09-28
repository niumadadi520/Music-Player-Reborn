package com.mengsama.mod.mengsamanetmusic.gui;

import com.mengsama.mod.mengsamanetmusic.api.NetEaseSearchResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SongFavoriteActionsTest {
    @Test void searchFavoritesPreserveProviderIdentityAndMetadataWithoutResolvingAudio() {
        var row = new NetEaseSearchResult("123456", "歌曲", "歌手", true, "netease", "",
                "https://p1.music.126.net/album.jpg", "专辑", 0);
        var song = SongFavoriteActions.fromSearchResult(row);
        assertEquals("netease:123456", song.identityKey());
        assertEquals("https://music.163.com/song?id=123456", song.rawUrl);
        assertEquals("专辑", song.albumName);
        assertEquals("歌手", song.artists.get(0));
        assertEquals(0, song.songTime, "missing duration must not be invented for saved metadata");
        assertEquals("", song.resolvedMediaUrl);
        assertTrue(song.playbackHeaders.isEmpty());
        var hydratedRequest = SongFavoriteActions.toSearchResult(song);
        assertEquals(row.getSongId(), hydratedRequest.getSongId());
        assertEquals(row.getSource(), hydratedRequest.getSource());
        assertEquals(row.getCoverUrl(), song.coverUrl, "collecting preserves the original provider artwork URL");
        assertEquals(song.preferredCoverUrl(), hydratedRequest.getCoverUrl());
    }

    @Test void qqSearchFavoriteKeepsMidAndAlbumCoverForLaterPlayback() {
        var row = new NetEaseSearchResult("003abcMID", "QQ歌曲", "歌手", false, "qq", "003album",
                "https://y.gtimg.cn/music/photo_new/T002R300x300M000003album.jpg", "专辑", 210);
        var song = SongFavoriteActions.fromSearchResult(row);
        assertEquals("qq:003abcMID", song.identityKey());
        assertEquals("https://y.qq.com/n/ryqq/songDetail/003abcMID", song.rawUrl);
        assertEquals("003album", SongFavoriteActions.toSearchResult(song).getAlbumMid());
        assertTrue(song.isValid());
    }

    @Test void favoriteAndDestructiveActionTargetsAreDisjoint() {
        int x = 10, y = 20, width = 340, height = 24;
        int favoriteX = x + width - SongRowRenderer.ACTION_WIDTH - SongRowRenderer.ACTION_GAP
                - SongRowRenderer.FAVORITE_WIDTH;
        assertTrue(SongRowRenderer.hitFavorite(favoriteX + 1, y + 8, x, y, width, height));
        assertFalse(SongRowRenderer.hitAction(favoriteX + 1, y + 8, x, y, width, height));
        int actionX = x + width - SongRowRenderer.ACTION_WIDTH;
        assertTrue(SongRowRenderer.hitAction(actionX + 1, y + 8, x, y, width, height));
        assertFalse(SongRowRenderer.hitFavorite(actionX + 1, y + 8, x, y, width, height));
        assertFalse(SongRowRenderer.hitFavorite(favoriteX + 1, y + height, x, y, width, height));
    }
}

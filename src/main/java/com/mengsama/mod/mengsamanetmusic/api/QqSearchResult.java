package com.mengsama.mod.mengsamanetmusic.api;

import java.util.Objects;

 


public record QqSearchResult(String id, String title, String singer, boolean vip, String albumMid, String albumPmId, String coverUrl, String albumName, int duration) {

    public QqSearchResult {
        id = clean(id);
        title = clean(title);
        singer = clean(singer);
        albumMid = clean(albumMid);
        albumPmId = clean(albumPmId);
        coverUrl = clean(coverUrl);
        albumName = clean(albumName);
        duration = Math.max(0, duration);
    }

    public QqSearchResult(String id, String title, String singer, boolean vip) {
        this(id, title, singer, vip, "", "", "", "", 0);
    }

    public QqSearchResult(String id, String title, String singer, boolean vip, String mid, String pmid, String cover) {
        this(id, title, singer, vip, mid, pmid, cover, "", 0);
    }

    private static String clean(String value) {
        return Objects.requireNonNullElse(value, "").strip();
    }

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getSinger() {
        return singer;
    }

    public boolean isVip() {
        return vip;
    }

    public String getAlbumMid() {
        return albumMid;
    }

    public String getAlbumPmId() {
        return albumPmId;
    }

    public String getCoverUrl() {
        return coverUrl;
    }

    public String getAlbumName() {
        return albumName;
    }

    public int getDuration() {
        return duration;
    }

    public String getDisplayText() {
        return title.isBlank() ? (singer.isBlank() ? "空" : singer) : singer.isBlank() ? title : title + " - " + singer;
    }
}

package com.mengsama.mod.mengsamanetmusic.api;

public class NetEaseSearchResult {
    private final String songId;
    private final String songName;
    private final String artistName;
    private final boolean isVip;
    private final String source;
    private final String albumMid;
    private final String coverUrl;
    private final String albumName;
    private final int duration;

    public NetEaseSearchResult(String songId, String songName, String artistName, boolean isVip) {
        this(songId, songName, artistName, isVip, "netease", "", "", "", 0);
    }

    public NetEaseSearchResult(String songId, String songName, String artistName, boolean isVip, String source) {
        this(songId, songName, artistName, isVip, source, "", "", "", 0);
    }

    public NetEaseSearchResult(String songId, String songName, String artistName, boolean isVip,
                               String source, String albumMid, String coverUrl) {
        this(songId, songName, artistName, isVip, source, albumMid, coverUrl, "", 0);
    }

    public NetEaseSearchResult(String songId, String songName, String artistName, boolean isVip,
                               String source, String albumMid, String coverUrl, String albumName, int duration) {
        this.songId = songId;
        this.songName = songName;
        this.artistName = artistName;
        this.isVip = isVip;
        this.source = source == null ? "netease" : source;
        this.albumMid = albumMid == null ? "" : albumMid;
        this.coverUrl = coverUrl == null ? "" : coverUrl;
        this.albumName = albumName == null ? "" : albumName;
        this.duration = Math.max(0, duration);
    }

    public String getSongId() { return songId; }
    public String getSongName() { return songName; }
    public String getArtistName() { return artistName; }
    public boolean isVip() { return isVip; }
    public String getSource() { return source; }
    public String getAlbumMid() { return albumMid; }
    public String getCoverUrl() { return coverUrl; }
    public String getAlbumName() { return albumName; }
    public int getDuration() { return duration; }
    public boolean isQq() { return "qq".equals(source); }
    public boolean isApple() { return "apple".equals(source); }

     
    public NetEaseSearchResult mergeDetail(SongInfo detail) {
        if (detail == null || isQq()) return this;
        detail.normalizeIdentity();
        String detailId = detail.providerId == null || detail.providerId.isBlank()
                ? Long.toString(detail.songId) : detail.providerId;
        if (!songId.equals(detailId)) return this;
        String mergedCover = coverUrl.isBlank() ? detail.preferredCoverUrl() : coverUrl;
        String mergedAlbum = albumName.isBlank() && detail.albumName != null ? detail.albumName : albumName;
        int mergedDuration = duration > 0 ? duration : detail.songTime;
        String mergedArtists = artistName;
        if ((mergedArtists == null || mergedArtists.isBlank()) && detail.artists != null) {
            mergedArtists = String.join(" / ", detail.artists);
        }
        return new NetEaseSearchResult(songId, songName, mergedArtists, isVip || detail.vip,
                source, albumMid, mergedCover, mergedAlbum, mergedDuration);
    }

    public String getDisplayText() {
        if (artistName != null && !artistName.isEmpty()) {
            return songName + " - " + artistName;
        }
        return songName;
    }
}

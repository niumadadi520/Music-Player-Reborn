package com.mengsama.mod.mengsamanetmusic.api;

import net.minecraft.nbt.CompoundTag;

import java.util.ArrayList;
import java.util.List;

public class SongInfo implements Cloneable {
    public String songUrl;
    public String songName;
    public int songTime;
    public String transName = "";
    public boolean vip;
    public boolean readOnly;
    public List<String> artists = new ArrayList<>();

    public String source = "unknown";
    public String providerId = "";
     
    public String rawUrl = "";
     
    public String picUrl = "";
     
    public String albumMid = "";
     
    public String coverUrl = "";
     
    public String albumName = "";
    public long songId = 0;
     
    public transient String resolvedMediaUrl = "";
     
    public transient java.util.Map<String, String> playbackHeaders = new java.util.HashMap<>();

    public SongInfo() {}

    public SongInfo(String songUrl, String songName, int songTime) {
        this.songUrl = songUrl;
        this.songName = songName;
        this.songTime = songTime;
    }

    public SongInfo(String songUrl, String songName, int songTime, boolean vip) {
        this(songUrl, songName, songTime);
        this.vip = vip;
    }

    public boolean isValid() {
        return songUrl != null && !songUrl.isBlank() && songName != null && !songName.isBlank() && songTime > 0;
    }

    public static String detectSource(String url) {
        if (url == null || url.isEmpty()) return "unknown";
        if (url.contains("music.163.com") || url.contains("163.com") || url.contains("netease")) return "netease";
        if (url.contains("qq.com") || url.contains("y.qq.com") || url.contains("dl.stream.qqmusic")) return "qq";
        if (url.contains("mzstatic.com") || url.contains("itunes.apple.com")) return "apple";
        return "unknown";
    }

    public void normalizeIdentity() {
        if (source == null || source.isBlank()) source = "unknown";
        if (providerId == null) providerId = "";
        if (rawUrl == null) rawUrl = "";
        if (songUrl == null) songUrl = "";
        if ("unknown".equals(source)) source = detectSource(!rawUrl.isBlank() ? rawUrl : songUrl);
        recoverLegacyIdentity();
        if (rawUrl.isBlank()) rawUrl = canonicalUrl(source, providerId, songId, songUrl);
        if ("netease".equals(source)) {
            if (songId <= 0 && providerId.matches("\\d+")) songId = parsePositiveId(providerId);
            if (songId <= 0) songId = extractNeteaseId(!rawUrl.isBlank() ? rawUrl : songUrl);
            if (providerId.isBlank() && songId > 0) providerId = Long.toString(songId);
            if (songId > 0 && isLikelyTemporaryUrl(rawUrl)) rawUrl = neteaseCanonical(songId);
            picUrl = CoverUrlUtil.normalize(picUrl);
            coverUrl = CoverUrlUtil.normalize(coverUrl);
            if (coverUrl.isBlank()) coverUrl = picUrl;
            if (picUrl.isBlank()) picUrl = coverUrl;
        } else if ("qq".equals(source)) {
            if (providerId.isBlank() && !songUrl.startsWith("http")) providerId = songUrl;
            if (!providerId.isBlank() && isLikelyTemporaryUrl(rawUrl)) rawUrl = qqCanonical(providerId);
            if ((coverUrl == null || coverUrl.isBlank()) && albumMid != null && !albumMid.isBlank()) {
                coverUrl = QqMusicUtils.buildAlbumCoverUrl(albumMid, "");
            }
            if ((picUrl == null || picUrl.isBlank()) && coverUrl != null) picUrl = coverUrl;
            if ((coverUrl == null || coverUrl.isBlank()) && picUrl != null) coverUrl = picUrl;
        } else if ("apple".equals(source)) {
            coverUrl = AppleMusicApi.highResolutionArtwork(coverUrl);
            picUrl = AppleMusicApi.highResolutionArtwork(picUrl);
            if (coverUrl == null || coverUrl.isBlank()) coverUrl = picUrl;
            if (picUrl == null || picUrl.isBlank()) picUrl = coverUrl;
        }
    }

    private void recoverLegacyIdentity() {
        String candidate = !rawUrl.isBlank() ? rawUrl : songUrl;
        if ("netease".equals(source)) {
            if (songId <= 0) songId = extractNeteaseId(candidate);
            if (providerId.isBlank() && songId > 0) providerId = Long.toString(songId);
        } else if ("qq".equals(source) && providerId.isBlank()) {
            java.util.regex.Matcher matcher = java.util.regex.Pattern.compile(
                    "(?:songmid|song_mid|mid)=([A-Za-z0-9]+)|/songDetail/([A-Za-z0-9]+)",
                    java.util.regex.Pattern.CASE_INSENSITIVE).matcher(candidate);
            if (matcher.find()) providerId = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
        }
    }

    private static String canonicalUrl(String source, String providerId, long songId, String fallback) {
        if ("netease".equals(source) && songId > 0) return neteaseCanonical(songId);
        if ("netease".equals(source) && providerId != null && providerId.matches("\\d+")) {
            return neteaseCanonical(parsePositiveId(providerId));
        }
        if ("qq".equals(source) && providerId != null && !providerId.isBlank()) return qqCanonical(providerId);
        return fallback == null ? "" : fallback;
    }

    private static long parsePositiveId(String value) {
        try { return Math.max(0L, Long.parseLong(value)); } catch (NumberFormatException ignored) { return 0L; }
    }

    private static String neteaseCanonical(long id) {
        return "https://music.163.com/song/media/outer/url?id=" + id + ".mp3";
    }

    private static String qqCanonical(String id) {
        return "https://y.qq.com/n/ryqq/songDetail/" + id;
    }

    public static boolean isLikelyTemporaryUrl(String value) {
        if (value == null || value.isBlank()) return false;
        String lower = value.toLowerCase(java.util.Locale.ROOT);
        return lower.contains("vkey=") || lower.contains("sign=") || lower.contains("signature=")
                || lower.contains("token=") || lower.contains("expires=") || lower.contains("expire=")
                || lower.contains("ws.stream.qqmusic.qq.com") || lower.contains("dl.stream.qqmusic.qq.com")
                || lower.contains("m701.music.126.net") || lower.contains("m801.music.126.net");
    }

    public String preferredCoverUrl() {
        normalizeIdentity();
        String preferred = coverUrl != null && !coverUrl.isBlank() ? coverUrl : picUrl;
        if ("apple".equals(source)) return AppleMusicApi.highResolutionArtwork(preferred);
        return "netease".equals(source) ? CoverUrlUtil.forDisplay(preferred) : CoverUrlUtil.normalize(preferred);
    }

    public String identityKey() {
        normalizeIdentity();
        String id = providerId == null ? "" : providerId.trim();
        if (id.isEmpty() && songId > 0) id = Long.toString(songId);
        return id.isEmpty() ? "" : source + ":" + id;
    }

    public boolean sameIdentity(SongInfo other) {
        if (other == null) return false;
        String key = identityKey();
        return !key.isEmpty() && key.equals(other.identityKey());
    }

    public boolean canRefreshProvider() {
        normalizeIdentity();
        String original = rawUrl == null ? "" : rawUrl.toLowerCase(java.util.Locale.ROOT);
        if (original.startsWith("file:") || original.startsWith("jar:") || original.startsWith("cache:")) return false;
        if (original.contains("meting") || original.contains("api.injahow.cn") || original.contains("music.gdstudio.app")) return false;
        return ("qq".equals(source) && providerId != null && !providerId.isBlank())
                || ("netease".equals(source) && (songId > 0 || providerId != null && providerId.matches("\\d+")));
    }

    private static long extractNeteaseId(String url) {
        if (url == null) return 0;
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(?:[?&]id=)(\\d+)").matcher(url);
        if (!matcher.find()) return 0;
        try { return Long.parseLong(matcher.group(1)); } catch (NumberFormatException ignored) { return 0; }
    }

    public static String getSourceDisplayName(String source) {
        return switch (source) {
            case "netease" -> "\u7F51\u6613\u4E91";
            case "qq" -> "QQ\u97F3\u4E50";
            case "apple" -> "Apple Music";
            default -> "\u672A\u77E5";
        };
    }

    public static int getSourceColor(String source) {
        return switch (source) { case "netease" -> 0xFFE60026; case "qq" -> 0xFF31C27C; case "apple" -> 0xFFFA586A; default -> 0xFFB8B8CC; };
    }

     
    @Override public SongInfo clone() { return SongTagCodec.copy(this); }
    public static void serializeNBT(SongInfo info, CompoundTag tag) { SongTagCodec.write(info, tag); }
    public static SongInfo deserializeNBT(CompoundTag tag) { return SongTagCodec.read(tag); }

    static int normalizeLegacySongTime(long value) {
        if (value <= 0) return 0;
        long seconds = value >= 10_000L ? value / 1000L : value;
        return (int) Math.min(Integer.MAX_VALUE, seconds);
    }
}

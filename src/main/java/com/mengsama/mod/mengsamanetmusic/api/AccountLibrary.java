package com.mengsama.mod.mengsamanetmusic.api;

import com.google.gson.*;
import com.mengsama.mod.mengsamanetmusic.api.qq.*;
import java.io.IOException;
import java.net.URI;
import java.util.*;
import java.util.function.BooleanSupplier;
import static com.mengsama.mod.mengsamanetmusic.api.qq.QqFields.*;

 
public final class AccountLibrary {
    public record Playlist(String id, String name) {}
    public record Result(String name, List<SongInfo> songs, String note) {
        public Result { songs = List.copyOf(songs); }
    }
    private static final int LIMIT = 10000;
    private final QqHttp.Transport http;
    public AccountLibrary(QqHttp.Transport http) { this.http = http; }
    private JsonObject get(URI uri, Map<String,String> headers) throws IOException {
        return parse(http.exchange(QqHttp.Request.get(uri, headers, 4 * 1024 * 1024, false)).successful().text());
    }
    private static void current(BooleanSupplier guard) throws IOException {
        if (!guard.getAsBoolean() || Thread.currentThread().isInterrupted()) throw new IOException("账号已切换，请重新读取");
    }
    private JsonObject qq(QqCredential credential, String module, String method, JsonObject param) throws IOException {
        String cookie = credential.toCookieString();
        JsonObject body = QqProtocol.envelope("2121", "library", QqProtocol.call(module, method, param));
        JsonObject comm = object(body,"comm");
        for (String field : List.of("uin","uid","qq","loginUin")) comm.addProperty(field,credential.account());
        comm.addProperty("authst",credential.accessKey());
        comm.addProperty("tmeLoginType",credential.accessKey().startsWith("W_X") ? "1" : "2");
        comm.addProperty("tmeAppID","qqmusic");comm.addProperty("ct",11);comm.addProperty("cv",13020508);comm.addProperty("v",13020508);
        return data(parse(http.exchange(QqHttp.Request.post(QqProtocol.DETAIL, body.toString(),
                QqProtocol.headers(cookie, true), false)).successful().text()), "library");
    }
    public Result qqLiked(QqCredential credential, BooleanSupplier guard) throws IOException {
        if (credential == null || !credential.isValid()) throw new IOException("请先登录 QQ 音乐账号");
        current(guard);
        String cookie = credential.toCookieString();
        JsonObject profile = get(QqProtocol.query("https://c6.y.qq.com/rsc/fcgi-bin/fcg_get_profile_homepage.fcg",
                Map.of("ct", "20", "cv", "4747474", "cid", "205360838", "userid", credential.account(), "format", "json")),
                QqProtocol.headers(cookie, false));
        if (number(profile, "code", 0) != 0) throw new IOException("QQ 账号信息读取失败，请重新登录");
        String encrypted = text(object(profile, "data", "creator"), "encrypt_uin");
        if (encrypted.isBlank()) throw new IOException("QQ 未返回当前账号标识，请重新登录");
        LinkedHashMap<String, SongInfo> songs = new LinkedHashMap<>();
        for (int offset = 0; offset < LIMIT; offset += 100) {
            current(guard);
            JsonObject page = qq(credential, "music.srfDissInfo.DissInfo", "CgiGetDiss",
                    properties("disstid", 0, "dirid", 201, "song_begin", offset, "song_num", 100, "enc_host_uin", encrypted));
            if (!page.has("songlist") || !page.get("songlist").isJsonArray()) throw new IOException("QQ 歌单格式发生变化，请稍后重试");
            JsonArray entries = rows(page, "songlist");
            int before = songs.size();
            for (JsonElement entry : entries) if (entry.isJsonObject()) {
                QqTrack track = QqTrack.read(entry.getAsJsonObject());
                if (track != null) songs.putIfAbsent(track.mid(), track.song());
            }
            long total = number(page, "total_song_num", -1);
            if (entries.isEmpty() && total > offset) throw new IOException("QQ 歌单分页不完整，请重试");
            if (!entries.isEmpty() && before == songs.size()) throw new IOException("QQ 歌单返回重复页面，请重试");
            if (total >= 0 ? offset + entries.size() >= total : entries.size() < 100) {
                current(guard); return new Result("QQ 音乐 · 我喜欢", new ArrayList<>(songs.values()), "只读取账号歌曲；☆ 收藏到本地");
            }
        }
        return new Result("QQ 音乐 · 我喜欢", new ArrayList<>(songs.values()), "已读取前 10000 首；超过本次读取上限");
    }
    public Result netEaseLiked(String cookie, BooleanSupplier guard) throws IOException {
        if (cookie == null || cookie.isBlank() || cookie.chars().anyMatch(c -> c < 32 || c == 127)) throw new IOException("请先填写网易云登录 Cookie");
        Map<String,String> headers = Map.of("Cookie", cookie, "Referer", "https://music.163.com/", "User-Agent", QqProtocol.USER_AGENT);
        current(guard);
        long uid = NetEaseQrLogin.accountId(http, cookie);
        current(guard);
        JsonObject likes = netEase("song/like/get", Map.of("uid", Long.toString(uid)), headers);
        if (!likes.has("ids") || !likes.get("ids").isJsonArray()) throw new IOException("网易云未返回喜欢的歌曲列表");
        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        for (JsonElement id : rows(likes,"ids")) try { if (id.getAsLong() > 0) ids.add(id.getAsLong()); } catch (RuntimeException ignored) {}
        List<Long> selected = ids.stream().limit(LIMIT).toList();
        Map<Long,SongInfo> decoded = new HashMap<>();
        for (int offset = 0; offset < selected.size(); offset += 200) {
            current(guard);
            JsonArray batch = new JsonArray();
            for (long id : selected.subList(offset, Math.min(offset + 200, selected.size()))) batch.add(properties("id", id));
            JsonObject detail = netEase("v3/song/detail", Map.of("c", batch.toString()), headers);
            if (!detail.has("songs") || !detail.get("songs").isJsonArray()) throw new IOException("网易云歌曲详情读取不完整");
            for (JsonElement item : rows(detail,"songs")) if (item.isJsonObject()) {
                SongInfo song = NetEaseTrackDecoder.decode(item.getAsJsonObject());
                if (song != null) decoded.put(song.songId, song);
            }
        }
        current(guard);
        List<SongInfo> songs = selected.stream().map(decoded::get).filter(Objects::nonNull).toList();
        String note = songs.size() < selected.size() ? "部分下架歌曲未返回详情" : "只读取账号歌曲；☆ 收藏到本地";
        if (ids.size() > LIMIT) note = "已读取前 10000 首；超过本次读取上限";
        return new Result("网易云 · 我喜欢的音乐", songs, note);
    }
    private JsonObject netEase(String path, Map<String,String> query, Map<String,String> headers) throws IOException {
        JsonObject root = get(QqProtocol.query("https://music.163.com/api/" + path, query), headers);
        if (number(root,"code",0) != 200) throw new IOException("网易云拒绝请求，请确认登录状态后重试");
        return root;
    }
    public List<Playlist> applePlaylists(Map<String,String> headers, BooleanSupplier guard) throws IOException {
        List<Playlist> result = new ArrayList<>();
        for (JsonObject item : applePages("/v1/me/library/playlists?limit=100", headers, guard)) {
            String id = text(item,"id"), name = text(object(item,"attributes"),"name");
            if (!id.isBlank()) result.add(new Playlist(id,name));
        }
        return List.copyOf(result);
    }
    public Result applePlaylist(Playlist playlist, Map<String,String> headers, BooleanSupplier guard) throws IOException {
        if (!playlist.id().matches("[A-Za-z0-9.\\-]+")) throw new IOException("Apple 歌单标识无效");
        List<SongInfo> songs = new ArrayList<>();
        for (JsonObject item : applePages("/v1/me/library/playlists/" + playlist.id() + "/tracks?limit=100&include=catalog", headers, guard)) {
            JsonArray catalog = rows(object(item,"relationships","catalog"),"data");
            if (catalog.isEmpty() || !catalog.get(0).isJsonObject()) continue;
            JsonObject track = catalog.get(0).getAsJsonObject(), attr = object(track,"attributes");
            SongInfo song = new SongInfo();
            song.source = "apple"; song.providerId = text(track,"id"); song.songName = text(attr,"name");
            song.artists = new ArrayList<>(List.of(text(attr,"artistName"))); song.albumName = text(attr,"albumName");
            JsonArray previews = rows(attr,"previews");
            if (!previews.isEmpty() && previews.get(0).isJsonObject()) song.songUrl = text(previews.get(0).getAsJsonObject(),"url");
            song.songTime = 30;
            song.coverUrl = text(object(attr,"artwork"),"url").replace("{w}","300").replace("{h}","300");
            song.picUrl = song.coverUrl;
            song.normalizeIdentity();
            if (!song.providerId.isBlank()) songs.add(song);
        }
        return new Result("Apple Music · " + playlist.name(), songs, "保持原有试听模式；云盘歌曲可能没有可用试听");
    }
    private List<JsonObject> applePages(String path, Map<String,String> headers, BooleanSupplier guard) throws IOException {
        if (!headers.containsKey("Music-User-Token")) throw new IOException("请先授权 Apple Music");
        List<JsonObject> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        while (!path.isBlank()) {
            current(guard);
            URI uri = URI.create("https://api.music.apple.com").resolve(path);
            if (!"https".equals(uri.getScheme()) || !"api.music.apple.com".equals(uri.getHost())
                    || uri.getPort() != -1 || uri.getUserInfo() != null || !uri.getPath().startsWith("/v1/me/library/")) throw new IOException("Apple 分页地址无效");
            if (!seen.add(uri.toString()) || seen.size() > 100) throw new IOException("Apple 歌单分页超出上限");
            JsonObject page = get(uri, headers);
            if (!page.has("data") || !page.get("data").isJsonArray()) throw new IOException("Apple 未返回资料库内容");
            for (JsonElement item : rows(page,"data")) if (item.isJsonObject()) result.add(item.getAsJsonObject());
            if (result.size() > LIMIT) throw new IOException("Apple 歌单超过 10000 首读取上限");
            path = text(page,"next");
        }
        current(guard); return result;
    }
}

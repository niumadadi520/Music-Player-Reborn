package com.mengsama.mod.mengsamanetmusic.api;

import net.minecraft.nbt.*;
import java.util.*;
import java.util.function.*;

 
final class SongTagCodec {
    private record TextColumn(String key, String initial, Function<SongInfo,String> read,
                              BiConsumer<SongInfo,String> write, String... aliases) {
        void load(CompoundTag input, SongInfo output) {
            String value = text(input, key);
            if (value.isBlank()) value = text(input, aliases);
            write.accept(output, value.isBlank() ? initial : value);
        }
        void store(SongInfo input, CompoundTag output) {
            output.putString(key, Objects.requireNonNullElse(read.apply(input), initial));
        }
    }
    private static final List<TextColumn> TEXT = List.of(
        new TextColumn("url", "", s->s.songUrl, (s,v)->s.songUrl=v, "songUrl","Url","URL","SongUrl","SongURL"),
        new TextColumn("name", "", s->s.songName, (s,v)->s.songName=v, "songName","Name","SongName"),
        new TextColumn("trans_name", "", s->s.transName, (s,v)->s.transName=v, "transName","TransName"),
        new TextColumn("source", "unknown", s->s.source, (s,v)->s.source=v, "Source"),
        new TextColumn("providerId", "", s->s.providerId, (s,v)->s.providerId=v, "ProviderId","SongId","songID"),
        new TextColumn("rawUrl", "", s->s.rawUrl, (s,v)->s.rawUrl=v, "RawUrl","providerUrl","originalUrl"),
        new TextColumn("picUrl", "", s->s.picUrl, (s,v)->s.picUrl=v, "PicUrl","picURL","PicURL"),
        new TextColumn("coverUrl", "", s->s.coverUrl, (s,v)->s.coverUrl=v, "CoverUrl","coverURL","CoverURL"),
        new TextColumn("albumMid", "", s->s.albumMid, (s,v)->s.albumMid=v, "AlbumMid"),
        new TextColumn("albumName", "", s->s.albumName, (s,v)->s.albumName=v, "AlbumName","album","Album")
    );
    private SongTagCodec() {}

    static SongInfo copy(SongInfo input) {
        SongInfo output = new SongInfo();
        TEXT.forEach(column -> column.write.accept(output, column.read.apply(input)));
        output.songId = input.songId;
        output.songTime = input.songTime;
        output.readOnly = input.readOnly;
        output.vip = input.vip;
        output.resolvedMediaUrl = input.resolvedMediaUrl;
        if (input.artists != null) output.artists.addAll(input.artists);
        if (input.playbackHeaders != null) output.playbackHeaders.putAll(input.playbackHeaders);
        return output;
    }

    static void write(SongInfo song, CompoundTag destination) {
        if (song == null) return;
        song.normalizeIdentity();
        TEXT.forEach(column -> column.store(song, destination));
        destination.putLong("songId", song.songId);
        destination.putInt("time", song.songTime);
        destination.putBoolean("read_only", song.readOnly);
        destination.putBoolean("vip", song.vip);
        if (song.artists == null || song.artists.isEmpty()) return;
        ListTag names = new ListTag();
        song.artists.forEach(name -> names.add(StringTag.valueOf(name)));
        destination.put("artists", names);
    }

    static SongInfo read(CompoundTag input) {
        SongInfo song = new SongInfo("", "", 0);
        if (input == null) return song;
        TEXT.forEach(column -> column.load(input, song));
        song.songTime = SongInfo.normalizeLegacySongTime(number(input, "time","songTime","SongTime","Time"));
        song.songId = number(input, "songId","SongId","songID");
        song.vip = input.getBoolean("vip");
        song.readOnly = input.getBoolean(input.contains("read_only") ? "read_only" : "readOnly");
        if (song.rawUrl.isBlank()) song.rawUrl = song.songUrl;
        if (song.coverUrl.isBlank()) song.coverUrl = song.picUrl;
        if (song.picUrl.isBlank()) song.picUrl = song.coverUrl;
        song.artists.addAll(artists(input));
        song.normalizeIdentity();
        return song;
    }

    private static List<String> artists(CompoundTag input) {
        List<String> result = new ArrayList<>();
        if (input.contains("artists", Tag.TAG_LIST)) {
            input.getList("artists", Tag.TAG_STRING).forEach(value -> result.add(value.getAsString()));
            return result;
        }
         
        var numbered = new TreeMap<Integer,String>();
        int limit = input.getInt("artistCount");
        for (String name : input.getAllKeys()) {
            if (!name.startsWith("artist_")) continue;
            try {
                int index = Integer.parseInt(name.substring(7));
                String value = input.getString(name);
                if (index >= 0 && index < limit && name.equals("artist_" + index) && !value.isEmpty()) numbered.put(index, value);
            } catch (NumberFormatException ignored) { }
        }
        if (!numbered.isEmpty()) return new ArrayList<>(numbered.values());
        Arrays.stream(text(input, "Artists","artists","Artist","artist").split("\\s*(?:/|、|,|;|&|\\|)\\s*"))
                .filter(value -> !value.isBlank()).forEach(result::add);
        return result;
    }

    private static String text(CompoundTag input, String... alternatives) {
        for (String name : alternatives) {
            Tag value = input.get(name);
            if (value instanceof StringTag string && !string.getAsString().isBlank()) return string.getAsString();
        }
        return "";
    }
    private static long number(CompoundTag input, String... alternatives) {
        for (String name : alternatives) {
            Tag value = input.get(name);
            if (value instanceof NumericTag numeric) return numeric.getAsLong();
            if (value instanceof StringTag string) {
                try { return Long.parseLong(string.getAsString().trim()); }
                catch (NumberFormatException ignored) { }
            }
        }
        return 0;
    }
}

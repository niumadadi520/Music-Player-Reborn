package com.mengsama.mod.mengsamanetmusic.item;

import com.mengsama.mod.mengsamanetmusic.platform.ItemData;
import com.mengsama.mod.mengsamanetmusic.api.SongInfo;
import com.mengsama.mod.mengsamanetmusic.util.PlayMode;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

 
public class MusicListItem extends Item {
    public MusicListItem() { super(new Properties().stacksTo(1)); }
    @Override public int getMaxStackSize(ItemStack item) { return 1; }
    public static List<SongInfo> getSongInfoList(ItemStack item) {
        List<SongInfo> decoded = new ArrayList<>();
        PlaylistTagEditor.rows(item).forEach(row -> decoded.add(SongInfo.deserializeNBT((CompoundTag)row)));
        return decoded;
    }
    public static int getSongCount(ItemStack item) { return PlaylistTagEditor.rows(item).size(); }
    public static int getSongIndex(ItemStack item) { return item.getItem() instanceof MusicListItem ? PlaylistTagEditor.index(item) : -1; }
    public static SongInfo getSongInfo(ItemStack item) {
        ListTag rows = PlaylistTagEditor.rows(item);
        if (rows.isEmpty()) return null;
        int selected = PlaylistTagEditor.index(item);
        return SongInfo.deserializeNBT(rows.getCompound(selected < 0 || selected >= rows.size() ? 0 : selected));
    }
    public static void setSongIndex(ItemStack item, int index) { PlaylistTagEditor.edit(item, root -> root.putInt("index", index)); }
    public static void nextMusic(ItemStack item) {
        int count = getSongCount(item);
        if (count == 0) return;
        int next = PlayMode.nextTrack(getPlayMode(item), 0, getSongIndex(item), new int[]{count},
                bound -> ThreadLocalRandom.current().nextInt(bound)).songIndex();
        setSongIndex(item, next);
    }
    public static void deleteSong(ItemStack item, int index) {
        if (index < 0 || index >= getSongCount(item)) return;
        PlaylistTagEditor.edit(item, root -> { root.getList(PlaylistTagEditor.SONGS, Tag.TAG_COMPOUND).remove(index); root.putInt("index", 0); });
    }
    public static void moveSong(ItemStack item, int from, int to) {
        int size = getSongCount(item);
        if (from == to || from < 0 || to < 0 || from >= size || to >= size) return;
        PlaylistTagEditor.edit(item, root -> {
            ListTag rows = root.getList(PlaylistTagEditor.SONGS, Tag.TAG_COMPOUND);
            Tag original = rows.get(from); rows.set(from, rows.get(to)); rows.set(to, original);
        });
    }
    public static boolean containsSong(ItemStack item, SongInfo song) {
        return song != null && getSongInfoList(item).stream().anyMatch(song::sameIdentity);
    }
    public static ItemStack addSongInfo(SongInfo song, ItemStack item) {
        if (!containsSong(item, song)) PlaylistTagEditor.putSong(item, song, true);
        return item;
    }
    public static ItemStack setSongInfo(SongInfo song, ItemStack item) { PlaylistTagEditor.putSong(item, song, false); return item; }
    public static PlayMode getPlayMode(ItemStack item) {
        return ItemData.has(item) ? PlayMode.getMode(ItemData.nullable(item).getInt("play_mode")) : PlayMode.LOOP;
    }
    public static void setPlayMode(ItemStack item, PlayMode mode) {
        PlaylistTagEditor.edit(item, root -> root.putInt("play_mode", (mode == null ? PlayMode.LOOP : mode).ordinal()));
    }
    @Override public Component getName(ItemStack item) {
        SongInfo selected = getSongInfo(item);
        return selected != null && selected.songName != null && !selected.songName.isEmpty()
                ? Component.translatable("item.mengsamanetmusic.music_list.info", selected.songName)
                : Component.translatable("item.mengsamanetmusic.music_list.name", getSongCount(item));
    }
    @Override public void appendHoverText(ItemStack item, net.minecraft.world.item.Item.TooltipContext level, List<Component> lines, TooltipFlag context) {
        SongInfo song = getSongInfo(item);
        if (song == null) lines.add(Component.translatable("tooltips.mengsamanetmusic.playlist.empty").withStyle(ChatFormatting.RED));
        addDetail(lines, "tooltip.mengsamanetmusic.play_mode", getPlayMode(item).getName().getString(), ChatFormatting.GOLD);
        if (song == null) return;
        addDetail(lines, "tooltips.mengsamanetmusic.playlist.trans_name", song.transName, ChatFormatting.GOLD);
        addDetail(lines, "tooltips.mengsamanetmusic.playlist.artists", song.artists == null ? "" : String.join(" | ", song.artists), ChatFormatting.DARK_AQUA);
        int seconds = Math.max(0, song.songTime);
        String duration = String.format(Locale.ROOT, Component.translatable("tooltips.mengsamanetmusic.playlist.time.format").getString(),
                String.format(Locale.ROOT, "%02d", seconds / 60), String.format(Locale.ROOT, "%02d", seconds % 60));
        addDetail(lines, "tooltips.mengsamanetmusic.playlist.time", duration, ChatFormatting.DARK_PURPLE);
    }
    private static void addDetail(List<Component> lines, String key, String value, ChatFormatting color) {
        if (value == null || value.isBlank()) return;
        lines.add(Component.literal("▍ ").withStyle(ChatFormatting.GREEN).append(Component.translatable(key).withStyle(ChatFormatting.GRAY))
                .append(Component.literal(": " + value).withStyle(color)));
    }
}

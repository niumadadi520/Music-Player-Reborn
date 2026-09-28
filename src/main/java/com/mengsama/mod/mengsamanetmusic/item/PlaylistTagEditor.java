package com.mengsama.mod.mengsamanetmusic.item;

import com.mengsama.mod.mengsamanetmusic.api.SongInfo;
import net.minecraft.nbt.*;
import net.minecraft.world.item.ItemStack;
import java.util.function.Consumer;

 
final class PlaylistTagEditor {
    static final String SONGS = "NetMusicSongInfoList";
    static ListTag rows(ItemStack item) {
        return item.getItem() instanceof MusicListItem && item.hasTag()
                ? item.getTag().getList(SONGS, Tag.TAG_COMPOUND) : new ListTag();
    }
    static int index(ItemStack item) { return item.hasTag() ? item.getTag().getInt("index") : 0; }
    static void edit(ItemStack item, Consumer<CompoundTag> operation) {
        if (!(item.getItem() instanceof MusicListItem)) return;
        CompoundTag copy = item.hasTag() ? item.getTag().copy() : new CompoundTag();
        operation.accept(copy);
        item.setTag(copy);
    }
    static void putSong(ItemStack item, SongInfo song, boolean append) {
        if (song == null || !(item.getItem() instanceof MusicListItem)) return;
        edit(item, root -> {
            ListTag rows = root.getList(SONGS, Tag.TAG_COMPOUND);
            int selected = index(item);
            int at = append ? rows.size() : selected >= 0 && selected < rows.size() ? selected : 0;
            if (!append) root.putInt("index", at);
            CompoundTag updated = at < rows.size() ? rows.getCompound(at).copy() : new CompoundTag();
            SongInfo.serializeNBT(song, updated);
            if (at < rows.size()) rows.set(at, updated);
            else { rows.add(updated); if (!append || rows.size() == 1) root.putInt("index", rows.size() - 1); }
            root.put(SONGS, rows);
        });
    }
}

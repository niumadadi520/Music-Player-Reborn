package com.mengsama.mod.mengsamanetmusic.listening;

import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.*;

 
public final class ListeningData extends SavedData {
    final ListeningLedger ledger = new ListeningLedger();
    private CompoundTag envelope = new CompoundTag();
    private boolean futureVersion;
    private long cachedRevision = -1;
    private long cachedTick = Long.MIN_VALUE;
    private List<ListeningLedger.Row> songCache = List.of(), playerCache = List.of();
    public static ListeningData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(ListeningData::load,ListeningData::new,"mengsamanetmusic_listening");
    }
    static ListeningData load(CompoundTag tag) {
        ListeningData data = new ListeningData();
        data.envelope = tag.copy(); data.futureVersion = tag.getInt("Version") > 1;
        for (boolean players : new boolean[]{false,true}) {
            ListTag list = tag.getList(players ? "Players" : "Songs",Tag.TAG_COMPOUND);
            for (int i=0;i<list.size();i++) {
                CompoundTag row=list.getCompound(i);
                data.ledger.restore(players,new ListeningLedger.Row(row.getString("Key"),row.getString("Title"),row.getString("Detail"),row.getLong("Value")));
            }
        }
        return data;
    }
    boolean writable() { return !futureVersion; }
    List<ListeningLedger.Row> ranking(boolean players, long tick) {
        if (cachedRevision < 0 || cachedRevision != ledger.revision() && tick - cachedTick >= 40) {
            songCache=ledger.ranked(false);playerCache=ledger.ranked(true);
            cachedRevision=ledger.revision();cachedTick=tick;
        }
        return players ? playerCache : songCache;
    }
    @Override public CompoundTag save(CompoundTag ignored) {
        CompoundTag result=envelope.copy();
        if (futureVersion) return result;
        result.putInt("Version",1);
        for (boolean players : new boolean[]{false,true}) {
            String field=players ? "Players" : "Songs";
            Map<String,CompoundTag> unknown=new HashMap<>();
            ListTag old=envelope.getList(field,Tag.TAG_COMPOUND);
            for (int i=0;i<old.size();i++) unknown.put(old.getCompound(i).getString("Key"),old.getCompound(i));
            ListTag rows=new ListTag();
            for (ListeningLedger.Row row : ledger.ranked(players)) {
                CompoundTag tag=unknown.getOrDefault(row.key(),new CompoundTag()).copy();
                tag.putString("Key",row.key());tag.putString("Title",row.title());tag.putString("Detail",row.detail());tag.putLong("Value",row.value());rows.add(tag);
            }
            result.put(field,rows);
        }
        return result;
    }
}

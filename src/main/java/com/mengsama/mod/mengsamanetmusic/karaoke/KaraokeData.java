package com.mengsama.mod.mengsamanetmusic.karaoke;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

 
public final class KaraokeData extends SavedData {
    private final Set<UUID> issued = new HashSet<>();
    public static KaraokeData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(KaraokeData::load, KaraokeData::new,
                "mengsamanetmusic_karaoke");
    }
    private static KaraokeData load(CompoundTag tag) {
        KaraokeData data = new KaraokeData();
        for (var entry : tag.getList("Microphones", 8)) {
            UUID id = KaraokeCode.parse(entry.getAsString());
            if (id != null) data.issued.add(id);
        }
        return data;
    }
    public boolean contains(UUID id) { return issued.contains(id); }
    public void remember(UUID id) { if (issued.add(id)) setDirty(); }
    public UUID create() {
        UUID id;
        do { id = UUID.randomUUID(); } while (issued.contains(id));
        remember(id);
        return id;
    }
    @Override public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        issued.stream().map(UUID::toString).sorted().forEach(id -> list.add(StringTag.valueOf(id)));
        tag.put("Microphones", list);
        return tag;
    }
}

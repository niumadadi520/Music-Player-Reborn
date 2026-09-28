package com.mengsama.mod.mengsamanetmusic.earbuds.client;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

 
public final class EarbudVisualState {
    public static final long MAX_AGE_TICKS = 40;
    private final Map<UUID,CompoundTag> devices = new HashMap<>();
    private final Map<UUID,CompoundTag> view = Collections.unmodifiableMap(devices);
    private Object world;
    private long receivedAt;
    public Map<UUID,CompoundTag> devices() { return view; }
    public void replace(CompoundTag snapshot, Object currentWorld, long now) {
        clear();
        if (currentWorld == null) return;
        world = currentWorld; receivedAt = now;
        for (var value : snapshot.getList("Devices", Tag.TAG_COMPOUND)) {
            CompoundTag row = (CompoundTag)value;
            if (row.hasUUID("Owner") && row.hasUUID("Device")) devices.put(row.getUUID("Owner"), row.copy());
        }
    }
    public boolean removeDevice(UUID device) {
        return device != null && devices.values().removeIf(row -> device.equals(row.getUUID("Device")));
    }
    public boolean expire(Object currentWorld, long now) {
        if (currentWorld != world || now < receivedAt || now - receivedAt > MAX_AGE_TICKS) {
            boolean hadDevices = !devices.isEmpty(); clear(); return hadDevices;
        }
        return false;
    }
    public void clear() { devices.clear(); world = null; receivedAt = 0; }
}

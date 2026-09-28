package com.mengsama.mod.mengsamanetmusic.block;

import com.mengsama.mod.mengsamanetmusic.platform.ItemData;
import net.minecraft.nbt.*;
import net.minecraft.world.item.*;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class DeviceStateCompatibilityTest {
    @BeforeAll static void bootstrap() throws Exception {
        com.mengsama.mod.mengsamanetmusic.testsupport.HeadlessEnvironment.initialize(); net.minecraft.SharedConstants.tryDetectVersion();
        var initialized = net.minecraft.server.Bootstrap.class.getDeclaredField("isBootstrapped");
        initialized.setAccessible(true); initialized.setBoolean(null, true);
        Class.forName("net.minecraft.core.registries.BuiltInRegistries");
    }
    @Test void all54LegacySlotsAndUnknownNestedFieldsRoundTrip() {
        ItemStackHandler oldInventory = new ItemStackHandler(54);
        for (int i = 0; i < 54; i++) {
            ItemStack item = new ItemStack(Items.MUSIC_DISC_13);
            ItemData.putInt(item, "index", 2);
            ItemData.putString(item, "OwnerNote", "编号" + i);
            ListTag songs = new ListTag();
            for (int n = 0; n < 3; n++) {
                CompoundTag song = new CompoundTag();
                song.putString("name", "歌" + i + ":" + n);
                song.putByteArray("FutureData", new byte[]{0, 5, -1});
                songs.add(song);
            }
            ItemData.put(item, "NetMusicSongInfoList", songs);
            oldInventory.setStackInSlot(i, item);
        }
        CompoundTag legacy = new CompoundTag();
        CompoundTag inventory = oldInventory.serializeNBT(com.mengsama.mod.mengsamanetmusic.platform.GameRegistries.lookup());
        inventory.putString("FutureInventoryField", "保留");
        legacy.put("ItemStacksCD", inventory);
        legacy.putInt("PlayIndex", 53);
        legacy.putInt("PlayMode", 2);
        DevicePlaylist queue = new DevicePlaylist(() -> fail("Loading must not notify a world"));
        queue.read(legacy);
        CompoundTag restored = new CompoundTag(); queue.write(restored);
        assertEquals(legacy, restored);
        assertEquals(53, queue.selection());
        for (int i = 0; i < 54; i++) assertEquals(((net.minecraft.nbt.CompoundTag)oldInventory.getStackInSlot(i).saveOptional(com.mengsama.mod.mengsamanetmusic.platform.GameRegistries.lookup())),
                queue.inventory().getStackInSlot(i).saveOptional(com.mengsama.mod.mengsamanetmusic.platform.GameRegistries.lookup()));
    }
    @Test void omittedLegacySizeAndFreshBlocksRemainUsable() {
        DevicePlaylist queue = new DevicePlaylist(() -> {});
        queue.read(new CompoundTag());
        assertEquals(54, queue.inventory().getSlots());
        CompoundTag old = new CompoundTag(); old.put("ItemStacksCD", new CompoundTag());
        queue.read(old); assertEquals(54, queue.inventory().getSlots());
    }
    @Test void pauseSurvivesSaveAndCompletionFiresExactlyOnce() {
        CompoundTag old = new CompoundTag();
        old.putBoolean("IsPlay", true); old.putBoolean("IsPaused", true);
        old.putInt("CurrentTime", 3); old.putBoolean("RedStoneSignal", true);
        DevicePlaybackClock clock = new DevicePlaybackClock(); clock.read(old);
        for (int i = 0; i < 100; i++) assertFalse(clock.advance());
        CompoundTag saved = new CompoundTag(); clock.write(saved); assertEquals(old, saved);
        clock.paused(false);
        assertFalse(clock.advance()); assertFalse(clock.advance()); assertTrue(clock.advance());
        for (int i = 0; i < 100; i++) assertFalse(clock.advance());
    }
    @Test void stopAndPendingResolutionDisarmOldCountdown() {
        DevicePlaybackClock clock = new DevicePlaybackClock(); clock.playing(true); clock.remaining(1);
        clock.remaining(0); assertFalse(clock.advance());
        clock.remaining(20); clock.clear();
        assertFalse(clock.playing()); assertFalse(clock.advance()); assertEquals(0, clock.remaining());
    }
}

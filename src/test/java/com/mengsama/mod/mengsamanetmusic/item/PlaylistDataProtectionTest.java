package com.mengsama.mod.mengsamanetmusic.item;

import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PlaylistDataProtectionTest {
    @BeforeAll static void bootstrap() throws Exception { MusicPlayerItemTest.bootstrapMinecraft(); }
    private static ItemStack cd(String name) {
        ItemStack stack = new ItemStack(Items.MUSIC_DISC_13);
        CompoundTag tag = stack.getOrCreateTag();
        tag.putString("CustomMarker", name);
        tag.putByteArray("UnknownBinaryField", new byte[]{0, 1, -1, 42});
        ListTag songs = new ListTag();
        for (int i = 0; i < 3; i++) {
            CompoundTag song = new CompoundTag();
            song.putString("name", name + "-song-" + i);
            song.putString("rawUrl", "https://example.invalid/" + name + "/" + i);
            song.putString("coverUrl", "https://example.invalid/cover.png");
            song.putString("FutureExtension", "preserve me");
            songs.add(song);
        }
        tag.put("NetMusicSongInfoList", songs);
        tag.putInt("index", 2);
        return stack;
    }
    private static ItemStack device(String... names) {
        ItemStack device = new ItemStack(Items.STICK);
        device.getOrCreateTag().putString("DeviceOwnerNote", "do not erase");
        CompoundTag inventory = new CompoundTag();
        inventory.putString("UnknownInventoryField", "retained");
        device.addTagElement("Item", inventory);
        NonNullList<ItemStack> cds = NonNullList.withSize(54, ItemStack.EMPTY);
        for (int i = 0; i < names.length; i++) cds.set(i, cd(names[i]));
        MusicPlayerItem.saveAllCdsToItem(device, cds);
        return device;
    }
    @Test void deletingBeforeLastPlayingTrackPreservesEveryRemainingCdByteForByte() {
        ItemStack device = device("A", "B", "C", "D");
        var before = MusicPlayerItem.loadAllCds(device);
        MusicPlayerItem.setPlayIndex(device, 3);
        MusicPlayerItem.setPlay(device, true);
        MusicPlayerItem.setCurrentTime(device, 1234);
        assertTrue(MusicPlayerItem.removePlaylistEntry(device, 0));
        var after = MusicPlayerItem.loadAllCds(ItemStack.of(device.save(new CompoundTag())));
        assertEquals(2, MusicPlayerItem.getPlayIndex(device));
        assertTrue(MusicPlayerItem.isPlay(device));
        assertEquals(1234, MusicPlayerItem.getCurrentTime(device));
        for (int i = 0; i < 3; i++) assertEquals(before.get(i + 1).save(new CompoundTag()), after.get(i).save(new CompoundTag()));
        assertEquals("retained", device.getTagElement("Item").getString("UnknownInventoryField"));
        assertEquals("do not erase", device.getTag().getString("DeviceOwnerNote"));
    }
    @Test void deletingCurrentTrackStopsAndDisarmsOldCountdown() {
        ItemStack device = device("A", "B", "C");
        MusicPlayerItem.setPlayIndex(device, 1);
        MusicPlayerItem.setPlay(device, true);
        MusicPlayerItem.setPaused(device, true);
        MusicPlayerItem.setCurrentTime(device, 500);
        assertTrue(MusicPlayerItem.removePlaylistEntry(device, 1));
        assertFalse(MusicPlayerItem.isPlay(device));
        assertFalse(MusicPlayerItem.isPaused(device));
        assertEquals(0, MusicPlayerItem.getCurrentTime(device));
        assertFalse(device.getTag().getBoolean("AutoAdvanceArmed"));
        assertEquals("C", MusicPlayerItem.loadAllCds(device).get(1).getTag().getString("CustomMarker"));
    }
    @Test void invalidDeletionDoesNotMutateAnyStoredData() {
        ItemStack device = device("A", "B");
        CompoundTag original = device.save(new CompoundTag());
        for (int index : new int[]{-1, 2, 54, Integer.MAX_VALUE}) assertFalse(MusicPlayerItem.removePlaylistEntry(device, index));
        assertEquals(original, device.save(new CompoundTag()));
    }
    @Test void full54SlotPlaylistSurvivesSaveLoadWithoutMetadataLoss() {
        NonNullList<ItemStack> all = NonNullList.withSize(54, ItemStack.EMPTY);
        for (int i = 0; i < 54; i++) all.set(i, cd("track-" + i));
        ItemStack device = device();
        MusicPlayerItem.saveAllCdsToItem(device, all);
        var restored = MusicPlayerItem.loadAllCds(ItemStack.of(device.save(new CompoundTag())));
        for (int i = 0; i < 54; i++) assertEquals(all.get(i).save(new CompoundTag()), restored.get(i).save(new CompoundTag()));
    }

    @Test void updatingSongMetadataPreservesUnknownFieldsAndOtherSongs() {
        MusicListItem musicList = new MusicListItem();
         
        net.minecraftforge.registries.ForgeRegistries.ITEMS.register(
                new net.minecraft.resources.ResourceLocation("mengsama_test", "metadata_playlist"), musicList);
        ItemStack stack = new ItemStack(musicList);
        stack.setTag(cd("nested").getTag().copy());
        CompoundTag first = stack.getTag().getList("NetMusicSongInfoList", 10).getCompound(0).copy();
        var song = new com.mengsama.mod.mengsamanetmusic.api.SongInfo("https://example.invalid/new.mp3", "Updated", 180);
        MusicListItem.setSongInfo(song, stack);
        var saved = stack.getTag().getList("NetMusicSongInfoList", 10);
        assertEquals(3, saved.size());
        assertEquals(first, saved.getCompound(0));
        assertEquals("preserve me", saved.getCompound(2).getString("FutureExtension"));
        assertEquals("Updated", saved.getCompound(2).getString("name"));
        assertEquals(2, MusicListItem.getSongIndex(stack));
    }
}

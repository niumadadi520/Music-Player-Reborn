package com.mengsama.mod.mengsamanetmusic.item;

import com.mengsama.mod.mengsamanetmusic.platform.ItemData;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MusicPlayerItemTest {
    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        com.mengsama.mod.mengsamanetmusic.testsupport.HeadlessEnvironment.initialize(); net.minecraft.SharedConstants.tryDetectVersion();
         
         
        var bootstrapped = net.minecraft.server.Bootstrap.class.getDeclaredField("isBootstrapped");
        bootstrapped.setAccessible(true);
        bootstrapped.setBoolean(null, true);
        Class.forName("net.minecraft.core.registries.BuiltInRegistries");
    }

    @Test
    void packPlaylistRemovesHolesAndPreservesOrder() {
        NonNullList<ItemStack> source = NonNullList.withSize(54, ItemStack.EMPTY);
        source.set(2, new ItemStack(Items.MUSIC_DISC_13));
        source.set(10, new ItemStack(Items.MUSIC_DISC_CAT));

        NonNullList<ItemStack> packed = MusicPlayerItem.packPlaylist(source);

        assertSame(Items.MUSIC_DISC_13, packed.get(0).getItem());
        assertSame(Items.MUSIC_DISC_CAT, packed.get(1).getItem());
        assertTrue(packed.get(2).isEmpty());
    }

    @Test
    void emptyPlaylistRemainsCanonical() {
        NonNullList<ItemStack> packed = MusicPlayerItem.packPlaylist(
                NonNullList.withSize(54, ItemStack.EMPTY));
        assertEquals(54, packed.size());
        assertTrue(packed.stream().allMatch(ItemStack::isEmpty));
    }

    @Test
    void authoritativePlaylistSurvivesMenuReopenAndUsesCompactIndices() {
        ItemStack device = new ItemStack(Items.STICK);
        NonNullList<ItemStack> source = NonNullList.withSize(54, ItemStack.EMPTY);
        source.set(4, new ItemStack(Items.MUSIC_DISC_13));
        source.set(20, new ItemStack(Items.MUSIC_DISC_CAT));

        MusicPlayerItem.saveAllCdsToItem(device, source);
        NonNullList<ItemStack> reopened = MusicPlayerItem.loadAllCds(device.copy());

        assertSame(Items.MUSIC_DISC_13, reopened.get(0).getItem());
        assertSame(Items.MUSIC_DISC_CAT, reopened.get(1).getItem());
        assertTrue(reopened.get(2).isEmpty());
    }

    @Test
    void readOnlyInstanceLookupDoesNotCreateRandomClientIdentity() {
        ItemStack partiallySynced = new ItemStack(Items.STICK);
        assertNull(MusicPlayerItem.getInstanceId(partiallySynced));
        assertFalse(ItemData.has(partiallySynced));

        java.util.UUID authoritative = MusicPlayerItem.getOrCreateInstanceId(partiallySynced);
        assertEquals(authoritative, MusicPlayerItem.getInstanceId(partiallySynced));
    }

    @Test
    void copiedDeviceKeepsPhysicalIdentityAcrossEntityEquipmentSync() {
        ItemStack equipped = new ItemStack(Items.STICK);
        java.util.UUID instance = MusicPlayerItem.getOrCreateInstanceId(equipped);
        MusicPlayerItem.setPlay(equipped, true);

        ItemStack syncedCopy = equipped.copy();
        assertNotSame(equipped, syncedCopy);
        assertEquals(instance, MusicPlayerItem.getInstanceId(syncedCopy));
        assertTrue(MusicPlayerItem.isPlay(syncedCopy));
    }

    @Test
    void pauseFreezesServerCountdownResumeContinuesAndStopClearsIt() {
        ItemStack device = new ItemStack(Items.STICK);
        MusicPlayerItem.setCurrentTime(device, 240);
        MusicPlayerItem.setPlay(device, true);
        MusicPlayerItem.tickTime(device);
        assertEquals(239, MusicPlayerItem.getCurrentTime(device));

        MusicPlayerItem.setPaused(device, true);
        assertTrue(MusicPlayerItem.isPlay(device), "pause must retain the active server session");
        assertTrue(MusicPlayerItem.isPaused(device));
         
        assertEquals(239, MusicPlayerItem.getCurrentTime(device));
        MusicPlayerItem.setPaused(device, false);
        MusicPlayerItem.tickTime(device);
        assertEquals(238, MusicPlayerItem.getCurrentTime(device));

        MusicPlayerItem.setPlay(device, false);
        assertFalse(MusicPlayerItem.isPaused(device), "stop clears the orthogonal pause bit");
        MusicPlayerItem.setCurrentTime(device, 0);
        assertEquals(0, MusicPlayerItem.getCurrentTime(device));
    }

    @Test
    void broadcastDefaultsOnAndPersistsAuthoritativeToggle() {
        ItemStack device = new ItemStack(Items.STICK);
        assertTrue(MusicPlayerItem.isBroadcast(device));

        MusicPlayerItem.setBroadcast(device, true);
        assertTrue(MusicPlayerItem.isBroadcast(device.copy()));
        MusicPlayerItem.setBroadcast(device, false);
        assertFalse(MusicPlayerItem.isBroadcast(device));
    }

    @Test
    void playableUrlValidationRejectsEmptyAndNonHttpSchemes() {
        assertFalse(MusicPlayerItem.hasPlayableUrl(new com.mengsama.mod.mengsamanetmusic.api.SongInfo("", "x", 1)));
        assertFalse(MusicPlayerItem.hasPlayableUrl(new com.mengsama.mod.mengsamanetmusic.api.SongInfo("file:///tmp/a.mp3", "x", 1)));
        assertTrue(MusicPlayerItem.hasPlayableUrl(new com.mengsama.mod.mengsamanetmusic.api.SongInfo("https://example.test/a.mp3", "x", 1)));
    }

    @Test
    void metadataAppendPersistsLastValidNestedPlaylistIndex() throws Exception {
        MusicListItem playlist = new MusicListItem();
        net.minecraft.core.Registry.register(net.minecraft.core.registries.BuiltInRegistries.ITEM,
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("mengsama_test", "index_playlist"), playlist);
        ItemStack item = new ItemStack(playlist);
        var song = new com.mengsama.mod.mengsamanetmusic.api.SongInfo("https://example.invalid/test.mp3", "Test", 180);
        MusicListItem.setSongIndex(item, 900);
        MusicListItem.setSongInfo(song, item);
        assertEquals(1, MusicListItem.getSongCount(item));
        assertEquals(0, MusicListItem.getSongIndex(item));
        var saved = ItemData.nullable(item).copy();
        assertEquals("Test", MusicListItem.getSongInfo(item).songName);
        assertEquals(saved, ItemData.nullable(item), "Reading a song must not rewrite its NBT");
        MusicListItem.deleteSong(item, -1);
        MusicListItem.moveSong(item, 0, 999);
        assertEquals(saved, ItemData.nullable(item), "Invalid edits must not erase songs");
    }
}

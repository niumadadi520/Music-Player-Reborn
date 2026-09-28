package com.mengsama.mod.mengsamanetmusic.karaoke;

import com.mengsama.mod.mengsamanetmusic.platform.ItemData;
import com.mengsama.mod.mengsamanetmusic.item.MusicPlayerItem;
import com.mengsama.mod.mengsamanetmusic.util.PlayMode;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class KaraokeMicrophoneDataTest {
    @BeforeAll static void bootstrap() throws Exception {
        com.mengsama.mod.mengsamanetmusic.testsupport.HeadlessEnvironment.initialize(); net.minecraft.SharedConstants.tryDetectVersion();
        var bootstrapped = net.minecraft.server.Bootstrap.class.getDeclaredField("isBootstrapped");
        bootstrapped.setAccessible(true);
        bootstrapped.setBoolean(null, true);
        Class.forName("net.minecraft.core.registries.BuiltInRegistries");
    }

    private static ItemStack song(String name) {
        ItemStack result = new ItemStack(Items.MUSIC_DISC_13);
        ItemData.putString(result, "CustomMarker", name);
        ItemData.update(result, tag -> tag.putByteArray("UnknownBinary", new byte[]{-1, 0, 1, 42}));
        ListTag songs = new ListTag();
        for (int i = 0; i < 3; i++) {
            CompoundTag track = new CompoundTag();
            track.putString("name", name + i);
            track.putString("rawUrl", "https://example.invalid/" + name + i);
            track.putString("FutureField", "keep");
            songs.add(track);
        }
        ItemData.put(result, "NetMusicSongInfoList", songs);
        ItemData.putInt(result, "index", 2);
        return result;
    }
    private static ItemStack template() {
        ItemStack result = new ItemStack(Items.STICK);
        ItemData.putString(result, "OwnerNote", "my microphone");
        ItemData.putUUID(result, KaraokeMicrophoneItem.ID_TAG, UUID.randomUUID());
        CompoundTag item = new CompoundTag();
        item.putString("UnknownInventoryField", "preserve");
        ItemData.put(result, "Item", item);
        return result;
    }
    private static ItemStackHandler sparseSongs() {
        ItemStackHandler inventory = new ItemStackHandler(54);
        inventory.setStackInSlot(0, song("A"));
        inventory.setStackInSlot(2, song("B"));
        return inventory;
    }

    @Test void importPreservesSparseSlotsAndSelectedTrackBeforeAnyGuiEdit() {
        ItemStack mic = template();
        NonNullList<ItemStack> songs = NonNullList.withSize(54, ItemStack.EMPTY);
        songs.set(0, song("A")); songs.set(2, song("B"));
        var stored=ItemData.child(mic,"Item");ContainerHelper.saveAllItems(stored,songs,com.mengsama.mod.mengsamanetmusic.platform.GameRegistries.lookup());ItemData.put(mic,"Item",stored);
        MusicPlayerItem.setPlayIndex(mic, 2); MusicPlayerItem.setPlayMode(mic, PlayMode.RANDOM);
        var imported = KaraokeMicrophoneData.playlist(mic);
        assertTrue(imported.get(1).isEmpty());
        assertEquals(((net.minecraft.nbt.CompoundTag)songs.get(2).saveOptional(com.mengsama.mod.mengsamanetmusic.platform.GameRegistries.lookup())), ((net.minecraft.nbt.CompoundTag)imported.get(2).saveOptional(com.mengsama.mod.mengsamanetmusic.platform.GameRegistries.lookup())));
        assertEquals(2, KaraokeMicrophoneData.playIndex(mic));
        assertEquals(PlayMode.RANDOM, KaraokeMicrophoneData.playMode(mic));
    }

    @Test void exportPreservesSelectedSongThroughCompactionAndCompleteUnknownMetadata() {
        ItemStack mic = template();
        UUID id = KaraokeMicrophoneItem.getId(mic);
        ItemStackHandler inventory = sparseSongs();
        CompoundTag original = ((net.minecraft.nbt.CompoundTag)mic.saveOptional(com.mengsama.mod.mengsamanetmusic.platform.GameRegistries.lookup()));
        ItemStack exported = KaraokeMicrophoneData.snapshot(mic, id, inventory, 2, PlayMode.RANDOM);
        assertEquals(id, KaraokeMicrophoneItem.getId(exported));
        assertEquals(1, MusicPlayerItem.getPlayIndex(exported));
        assertEquals(PlayMode.RANDOM, MusicPlayerItem.getPlayMode(exported));
        assertEquals(((net.minecraft.nbt.CompoundTag)inventory.getStackInSlot(2).saveOptional(com.mengsama.mod.mengsamanetmusic.platform.GameRegistries.lookup())), MusicPlayerItem.loadAllCds(exported).get(1).saveOptional(com.mengsama.mod.mengsamanetmusic.platform.GameRegistries.lookup()));
        assertEquals("preserve", ItemData.child(exported, "Item").getString("UnknownInventoryField"));
        assertEquals("my microphone", ItemData.nullable(exported).getString("OwnerNote"));
        assertEquals(original, ((net.minecraft.nbt.CompoundTag)mic.saveOptional(com.mengsama.mod.mengsamanetmusic.platform.GameRegistries.lookup())), "snapshot must not modify the template");
        ItemData.putString(exported, "OwnerNote", "changed copy");
        assertEquals("my microphone", ItemData.nullable(mic).getString("OwnerNote"));
    }

    @Test void deletingEarlierSlotWhileMountedExportsLiveRemainingSongAndDoesNotRestoreDeletedSong() {
        ItemStack mic = template();
        ItemStackHandler inventory = sparseSongs();
        CompoundTag expectedSong = ((net.minecraft.nbt.CompoundTag)inventory.getStackInSlot(2).saveOptional(com.mengsama.mod.mengsamanetmusic.platform.GameRegistries.lookup()));
         
        inventory.setStackInSlot(0, ItemStack.EMPTY);
        ItemStack exported = KaraokeMicrophoneData.snapshot(mic, KaraokeMicrophoneItem.getId(mic), inventory, 2, PlayMode.SEQUENTIAL);
        var imported = KaraokeMicrophoneData.playlist(ItemStack.parseOptional(com.mengsama.mod.mengsamanetmusic.platform.GameRegistries.lookup(), ((net.minecraft.nbt.CompoundTag)exported.saveOptional(com.mengsama.mod.mengsamanetmusic.platform.GameRegistries.lookup()))));
        assertEquals(0, KaraokeMicrophoneData.playIndex(exported));
        assertEquals(expectedSong, ((net.minecraft.nbt.CompoundTag)imported.get(0).saveOptional(com.mengsama.mod.mengsamanetmusic.platform.GameRegistries.lookup())));
        assertEquals(1, imported.stream().filter(stack -> !stack.isEmpty()).count());
        ItemData.putString(imported.get(0), "CustomMarker", "changed copy");
        assertEquals(expectedSong, ((net.minecraft.nbt.CompoundTag)inventory.getStackInSlot(2).saveOptional(com.mengsama.mod.mengsamanetmusic.platform.GameRegistries.lookup())));
    }

    @Test void oldBlockOnlyPayloadRetainsItsSparseSelectionAndRandomModeAndUnknownFields() {
        ItemStack mic = template(); ItemData.remove(mic, "Item");
        CompoundTag legacy = new CompoundTag();
        CompoundTag inventory = sparseSongs().serializeNBT(com.mengsama.mod.mengsamanetmusic.platform.GameRegistries.lookup());
        inventory.putString("UnknownInventoryField", "legacy kept");
        legacy.put("ItemStacksCD", inventory);
        legacy.putInt("PlayIndex", 2); legacy.putInt("PlayMode", PlayMode.RANDOM.ordinal());
        legacy.putString("FutureBlockField", "keep too");
        ItemData.put(mic, "KaraokeBlockData", legacy);
        assertEquals(2, KaraokeMicrophoneData.playIndex(mic));
        assertEquals(PlayMode.RANDOM, KaraokeMicrophoneData.playMode(mic));
        var songs = KaraokeMicrophoneData.playlist(mic);
        assertTrue(songs.get(1).isEmpty());
        ItemStackHandler live = new ItemStackHandler(54);
        for (int i = 0; i < 54; i++) live.setStackInSlot(i, songs.get(i));
        ItemStack exported = KaraokeMicrophoneData.snapshot(mic, KaraokeMicrophoneItem.getId(mic), live,
                KaraokeMicrophoneData.playIndex(mic), KaraokeMicrophoneData.playMode(mic));
        CompoundTag savedLegacy = ItemData.child(exported, "KaraokeBlockData");
        assertEquals(1, savedLegacy.getInt("PlayIndex"));
        assertEquals(PlayMode.RANDOM.ordinal(), savedLegacy.getInt("PlayMode"));
        assertEquals("legacy kept", savedLegacy.getCompound("ItemStacksCD").getString("UnknownInventoryField"));
        assertEquals("keep too", savedLegacy.getString("FutureBlockField"));
        assertEquals(2, savedLegacy.getCompound("ItemStacksCD").getList("Items", 10).size());
        MusicPlayerItem.setPlayIndex(mic, 0); MusicPlayerItem.setPlayMode(mic, PlayMode.LOOP);
        assertEquals(0, KaraokeMicrophoneData.playIndex(mic));
        assertEquals(PlayMode.LOOP, KaraokeMicrophoneData.playMode(mic));
    }

    @Test void exportedMicrophoneNeverCarriesAnActiveCaptureOrAutoplayPermission() {
        ItemStack mic = template();
        MusicPlayerItem.setPlay(mic, true); MusicPlayerItem.setPaused(mic, true); MusicPlayerItem.setCurrentTime(mic, 123);
        ItemData.putBoolean(mic, "AutoAdvanceArmed", true);
        ItemData.putBoolean(mic, "KaraokeRuntimeActive", true); ItemData.putLong(mic, "KaraokeVoiceUntil", 999);
        ItemStack exported = KaraokeMicrophoneData.snapshot(mic, KaraokeMicrophoneItem.getId(mic), sparseSongs(), 2, PlayMode.LOOP);
        assertFalse(MusicPlayerItem.isPlay(exported)); assertFalse(MusicPlayerItem.isPaused(exported));
        assertEquals(0, MusicPlayerItem.getCurrentTime(exported));
        assertFalse(ItemData.nullable(exported).contains("AutoAdvanceArmed"));
        assertFalse(ItemData.nullable(exported).contains("KaraokeRuntimeActive"));
        assertFalse(ItemData.nullable(exported).contains("KaraokeVoiceUntil"));
    }

    @Test void emptyStandOwnMetadataCannotHideAnotherMicrophoneOrItsSongs() {
        ItemStack stand = template(); stand.setCount(12);
        CompoundTag hidden = new CompoundTag();
        hidden.put("KaraokeMountedMicrophone", template().saveOptional(com.mengsama.mod.mengsamanetmusic.platform.GameRegistries.lookup()));
        hidden.put("ItemStacksCD", sparseSongs().serializeNBT(com.mengsama.mod.mengsamanetmusic.platform.GameRegistries.lookup()));
        hidden.putUUID("KaraokeDeviceId", UUID.randomUUID());
        hidden.putString("StandPaint", "rosewood");
        ItemData.put(stand, "BlockEntityTag", hidden.copy());
        ItemData.put(stand, "KaraokeBlockData", hidden.copy());
        ItemStack clean = KaraokeMicrophoneData.standOnly(stand);
        assertEquals(1, clean.getCount()); assertNull(KaraokeMicrophoneItem.getId(clean));
        assertFalse(ItemData.nullable(clean).contains("Item"));
        assertEquals("my microphone", ItemData.nullable(clean).getString("OwnerNote"));
        for (String payload : new String[]{"BlockEntityTag", "KaraokeBlockData"}) {
            assertEquals("rosewood", ItemData.child(clean, payload).getString("StandPaint"));
            assertFalse(ItemData.child(clean, payload).contains("KaraokeMountedMicrophone"));
            assertFalse(ItemData.child(clean, payload).contains("KaraokeDeviceId"));
            assertFalse(ItemData.child(clean, payload).contains("ItemStacksCD"));
        }
        assertTrue(ItemData.child(stand, "BlockEntityTag").contains("KaraokeMountedMicrophone"));
    }

    @Test void directPlacementEntryRejectsHandheldWithoutTouchingAWorldOrPlacementContext() {
        KaraokeMicrophoneItem handheld = new KaraokeMicrophoneItem(Blocks.STONE, "pink_handheld_microphone");
        assertTrue(handheld.isHandheld());
        assertEquals(InteractionResult.FAIL, handheld.place(null));
        assertFalse(new KaraokeMicrophoneItem(Blocks.STONE, "pink_microphone").isHandheld());
    }
}

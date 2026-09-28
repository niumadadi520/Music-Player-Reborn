package com.mengsama.mod.mengsamanetmusic.karaoke;

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
import net.minecraftforge.items.ItemStackHandler;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class KaraokeMicrophoneDataTest {
    @BeforeAll static void bootstrap() throws Exception {
        net.minecraft.SharedConstants.tryDetectVersion();
        var bootstrapped = net.minecraft.server.Bootstrap.class.getDeclaredField("isBootstrapped");
        bootstrapped.setAccessible(true);
        bootstrapped.setBoolean(null, true);
        Class.forName("net.minecraft.core.registries.BuiltInRegistries");
    }

    private static ItemStack song(String name) {
        ItemStack result = new ItemStack(Items.MUSIC_DISC_13);
        result.getOrCreateTag().putString("CustomMarker", name);
        result.getOrCreateTag().putByteArray("UnknownBinary", new byte[]{-1, 0, 1, 42});
        ListTag songs = new ListTag();
        for (int i = 0; i < 3; i++) {
            CompoundTag track = new CompoundTag();
            track.putString("name", name + i);
            track.putString("rawUrl", "https://example.invalid/" + name + i);
            track.putString("FutureField", "keep");
            songs.add(track);
        }
        result.getOrCreateTag().put("NetMusicSongInfoList", songs);
        result.getOrCreateTag().putInt("index", 2);
        return result;
    }
    private static ItemStack template() {
        ItemStack result = new ItemStack(Items.STICK);
        result.getOrCreateTag().putString("OwnerNote", "my microphone");
        result.getOrCreateTag().putUUID(KaraokeMicrophoneItem.ID_TAG, UUID.randomUUID());
        CompoundTag item = new CompoundTag();
        item.putString("UnknownInventoryField", "preserve");
        result.addTagElement("Item", item);
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
        ContainerHelper.saveAllItems(mic.getTagElement("Item"), songs);
        MusicPlayerItem.setPlayIndex(mic, 2); MusicPlayerItem.setPlayMode(mic, PlayMode.RANDOM);
        var imported = KaraokeMicrophoneData.playlist(mic);
        assertTrue(imported.get(1).isEmpty());
        assertEquals(songs.get(2).save(new CompoundTag()), imported.get(2).save(new CompoundTag()));
        assertEquals(2, KaraokeMicrophoneData.playIndex(mic));
        assertEquals(PlayMode.RANDOM, KaraokeMicrophoneData.playMode(mic));
    }

    @Test void exportPreservesSelectedSongThroughCompactionAndCompleteUnknownMetadata() {
        ItemStack mic = template();
        UUID id = KaraokeMicrophoneItem.getId(mic);
        ItemStackHandler inventory = sparseSongs();
        CompoundTag original = mic.save(new CompoundTag());
        ItemStack exported = KaraokeMicrophoneData.snapshot(mic, id, inventory, 2, PlayMode.RANDOM);
        assertEquals(id, KaraokeMicrophoneItem.getId(exported));
        assertEquals(1, MusicPlayerItem.getPlayIndex(exported));
        assertEquals(PlayMode.RANDOM, MusicPlayerItem.getPlayMode(exported));
        assertEquals(inventory.getStackInSlot(2).save(new CompoundTag()), MusicPlayerItem.loadAllCds(exported).get(1).save(new CompoundTag()));
        assertEquals("preserve", exported.getTagElement("Item").getString("UnknownInventoryField"));
        assertEquals("my microphone", exported.getTag().getString("OwnerNote"));
        assertEquals(original, mic.save(new CompoundTag()), "snapshot must not modify the template");
        exported.getTag().putString("OwnerNote", "changed copy");
        assertEquals("my microphone", mic.getTag().getString("OwnerNote"));
    }

    @Test void deletingEarlierSlotWhileMountedExportsLiveRemainingSongAndDoesNotRestoreDeletedSong() {
        ItemStack mic = template();
        ItemStackHandler inventory = sparseSongs();
        CompoundTag expectedSong = inventory.getStackInSlot(2).save(new CompoundTag());
         
        inventory.setStackInSlot(0, ItemStack.EMPTY);
        ItemStack exported = KaraokeMicrophoneData.snapshot(mic, KaraokeMicrophoneItem.getId(mic), inventory, 2, PlayMode.SEQUENTIAL);
        var imported = KaraokeMicrophoneData.playlist(ItemStack.of(exported.save(new CompoundTag())));
        assertEquals(0, KaraokeMicrophoneData.playIndex(exported));
        assertEquals(expectedSong, imported.get(0).save(new CompoundTag()));
        assertEquals(1, imported.stream().filter(stack -> !stack.isEmpty()).count());
        imported.get(0).getOrCreateTag().putString("CustomMarker", "changed copy");
        assertEquals(expectedSong, inventory.getStackInSlot(2).save(new CompoundTag()));
    }

    @Test void oldBlockOnlyPayloadRetainsItsSparseSelectionAndRandomModeAndUnknownFields() {
        ItemStack mic = template(); mic.getTag().remove("Item");
        CompoundTag legacy = new CompoundTag();
        CompoundTag inventory = sparseSongs().serializeNBT();
        inventory.putString("UnknownInventoryField", "legacy kept");
        legacy.put("ItemStacksCD", inventory);
        legacy.putInt("PlayIndex", 2); legacy.putInt("PlayMode", PlayMode.RANDOM.ordinal());
        legacy.putString("FutureBlockField", "keep too");
        mic.addTagElement("KaraokeBlockData", legacy);
        assertEquals(2, KaraokeMicrophoneData.playIndex(mic));
        assertEquals(PlayMode.RANDOM, KaraokeMicrophoneData.playMode(mic));
        var songs = KaraokeMicrophoneData.playlist(mic);
        assertTrue(songs.get(1).isEmpty());
        ItemStackHandler live = new ItemStackHandler(54);
        for (int i = 0; i < 54; i++) live.setStackInSlot(i, songs.get(i));
        ItemStack exported = KaraokeMicrophoneData.snapshot(mic, KaraokeMicrophoneItem.getId(mic), live,
                KaraokeMicrophoneData.playIndex(mic), KaraokeMicrophoneData.playMode(mic));
        CompoundTag savedLegacy = exported.getTagElement("KaraokeBlockData");
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
        mic.getTag().putBoolean("AutoAdvanceArmed", true);
        mic.getTag().putBoolean("KaraokeRuntimeActive", true); mic.getTag().putLong("KaraokeVoiceUntil", 999);
        ItemStack exported = KaraokeMicrophoneData.snapshot(mic, KaraokeMicrophoneItem.getId(mic), sparseSongs(), 2, PlayMode.LOOP);
        assertFalse(MusicPlayerItem.isPlay(exported)); assertFalse(MusicPlayerItem.isPaused(exported));
        assertEquals(0, MusicPlayerItem.getCurrentTime(exported));
        assertFalse(exported.getTag().contains("AutoAdvanceArmed"));
        assertFalse(exported.getTag().contains("KaraokeRuntimeActive"));
        assertFalse(exported.getTag().contains("KaraokeVoiceUntil"));
    }

    @Test void emptyStandOwnMetadataCannotHideAnotherMicrophoneOrItsSongs() {
        ItemStack stand = template(); stand.setCount(12);
        CompoundTag hidden = new CompoundTag();
        hidden.put("KaraokeMountedMicrophone", template().save(new CompoundTag()));
        hidden.put("ItemStacksCD", sparseSongs().serializeNBT());
        hidden.putUUID("KaraokeDeviceId", UUID.randomUUID());
        hidden.putString("StandPaint", "rosewood");
        stand.addTagElement("BlockEntityTag", hidden.copy());
        stand.addTagElement("KaraokeBlockData", hidden.copy());
        ItemStack clean = KaraokeMicrophoneData.standOnly(stand);
        assertEquals(1, clean.getCount()); assertNull(KaraokeMicrophoneItem.getId(clean));
        assertFalse(clean.getTag().contains("Item"));
        assertEquals("my microphone", clean.getTag().getString("OwnerNote"));
        for (String payload : new String[]{"BlockEntityTag", "KaraokeBlockData"}) {
            assertEquals("rosewood", clean.getTagElement(payload).getString("StandPaint"));
            assertFalse(clean.getTagElement(payload).contains("KaraokeMountedMicrophone"));
            assertFalse(clean.getTagElement(payload).contains("KaraokeDeviceId"));
            assertFalse(clean.getTagElement(payload).contains("ItemStacksCD"));
        }
        assertTrue(stand.getTagElement("BlockEntityTag").contains("KaraokeMountedMicrophone"));
    }

    @Test void directPlacementEntryRejectsHandheldWithoutTouchingAWorldOrPlacementContext() {
        KaraokeMicrophoneItem handheld = new KaraokeMicrophoneItem(Blocks.STONE, "pink_handheld_microphone");
        assertTrue(handheld.isHandheld());
        assertEquals(InteractionResult.FAIL, handheld.place(null));
        assertFalse(new KaraokeMicrophoneItem(Blocks.STONE, "pink_microphone").isHandheld());
    }
}

package com.mengsama.mod.mengsamanetmusic.platform;

import net.minecraft.core.NonNullList;
import net.minecraft.nbt.*;
import net.minecraft.world.item.*;
import com.mengsama.mod.mengsamanetmusic.item.MusicPlayerItem;
import org.junit.jupiter.api.*;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class NeoForgeDataMigrationTest {
    @BeforeAll static void bootstrap() throws Exception {
        net.minecraft.SharedConstants.tryDetectVersion();
        var field=net.minecraft.server.Bootstrap.class.getDeclaredField("isBootstrapped");
        field.setAccessible(true); field.setBoolean(null,true);
        Class.forName("net.minecraft.core.registries.BuiltInRegistries");
    }
    @Test void legacyNestedPlaylistPreservesSparseSlotsIdentityAndUnknownData() {
        var identity=UUID.randomUUID();
        var custom=new CompoundTag(); custom.putUUID("MusicPlayerInstanceId",identity);
        custom.putString("OwnerNote","保留歌单"); custom.putByteArray("Unknown",new byte[]{-1,0,42});
        var song=new CompoundTag(); song.putString("name","测试歌曲"); song.putString("FutureField","kept");
        var songs=new ListTag();songs.add(song);custom.put("NetMusicSongInfoList",songs);
        var oldItem=new CompoundTag(); oldItem.putString("id","minecraft:stick");oldItem.putByte("Count",(byte)1);
        oldItem.putByte("Slot",(byte)53);oldItem.put("tag",custom);
        var inventory=new CompoundTag();var entries=new ListTag();entries.add(oldItem);inventory.put("Items",entries);
        inventory.putString("UnknownInventoryField","retained");
        var original=inventory.copy();var device=new ItemStack(Items.STICK);ItemData.put(device,"Item",inventory);
        var loaded=MusicPlayerItem.loadAllCds(device);
        assertEquals(1,loaded.stream().filter(s->!s.isEmpty()).count());
        assertEquals(custom,ItemData.get(loaded.get(53)));
        assertEquals(original,inventory,"Migration must not mutate the original backup payload");
        MusicPlayerItem.saveAllCdsPreservingSlots(device,loaded);
        assertEquals("retained",ItemData.child(device,"Item").getString("UnknownInventoryField"));
        var restored=StoredItems.read(GameRegistries.lookup(),(CompoundTag)device.save(GameRegistries.lookup()));
        assertEquals(custom,ItemData.get(MusicPlayerItem.loadAllCds(restored).get(53)));
        assertEquals(identity,ItemData.get(MusicPlayerItem.loadAllCds(restored).get(53)).getUUID("MusicPlayerInstanceId"));
    }
    @Test void customDataCopiesCannotMutateSavedStacksAndWritesPreserveOtherComponents() {
        var device=new ItemStack(Items.STICK);var source=new CompoundTag();source.putString("Note","original");
        device.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,net.minecraft.network.chat.Component.literal("随身听"));
        ItemData.set(device,source);source.putString("Note","outside");
        var copy=ItemData.get(device);copy.putString("Note","outside2");
        ItemData.putBoolean(device,"IsPlay",true);
        assertEquals("original",ItemData.get(device).getString("Note"));
        assertTrue(ItemData.get(device).getBoolean("IsPlay"));
        assertEquals("随身听",device.get(net.minecraft.core.component.DataComponents.CUSTOM_NAME).getString());
        var snapshot=device.copy();ItemData.remove(device,"Note");
        assertEquals("original",ItemData.get(snapshot).getString("Note"));
    }
    @Test void legacyGramophonePayloadBecomesNativeBlockDataWithoutLosingItsPlaylist() {
        var inventory=new CompoundTag(); inventory.putString("FutureInventoryField","keep");
        var blockData=new CompoundTag(); blockData.put("ItemStacksCD",inventory);blockData.putInt("PlayIndex",12);
        var custom=new CompoundTag(); custom.put("BlockEntityTag",blockData);custom.putString("OwnerNote","keep");
        var oldItem=new CompoundTag();oldItem.putString("id","mengsamanetmusic:music_player_block");
        oldItem.putByte("Count",(byte)1);oldItem.put("tag",custom);
        var upgraded=StoredItems.upgrade(oldItem);var components=upgraded.getCompound("components");
        assertEquals(blockData,components.getCompound("minecraft:block_entity_data"));
        assertEquals("keep",components.getCompound("minecraft:custom_data").getString("OwnerNote"));
    }
    @Test void currentFormatDoesNotApplyLegacyDataFixesAgain() {
        var item=new ItemStack(Items.STICK);ItemData.putString(item,"Extension","kept");
        var saved=(CompoundTag)item.save(GameRegistries.lookup());var upgraded=StoredItems.upgrade(saved);
        assertEquals(saved,upgraded);assertNotSame(saved,upgraded);
    }
    private static final class TestDevice extends com.mengsama.mod.mengsamanetmusic.block.MusicDeviceEntity {
        TestDevice() { super(net.minecraft.world.level.block.entity.BlockEntityType.CHEST,
                net.minecraft.core.BlockPos.ZERO, net.minecraft.world.level.block.Blocks.CHEST.defaultBlockState()); }
    }
    @Test void placedDeviceSaveReloadPreservesComponentsAndCurrentSparsePlaylist() {
        var source=new ItemStack(Items.STICK);
        source.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,net.minecraft.network.chat.Component.literal("我的播放器"));
        ItemData.putString(source,"Extension","original");
        var device=new TestDevice();device.applyComponentsFromItemStack(source);
        var song=new ItemStack(Items.MUSIC_DISC_CAT);ItemData.putString(song,"SongNote","保留");
        device.getPlayerInv().setStackInSlot(53,song);device.setPlayIndex(53);
        var snapshot=device.saveWithoutMetadata(GameRegistries.lookup());
        var restored=new TestDevice();restored.loadWithComponents(snapshot,GameRegistries.lookup());
        var drop=new ItemStack(Items.STICK);drop.applyComponents(restored.collectComponents());
        ItemData.putBoolean(drop,"IsPlay",false);
        assertEquals("我的播放器",drop.get(net.minecraft.core.component.DataComponents.CUSTOM_NAME).getString());
        assertEquals("original",ItemData.get(drop).getString("Extension"));
        assertEquals(54,restored.getPlayerInv().getSlots());assertEquals(53,restored.getPlayIndex());
        assertTrue(ItemStack.isSameItemSameComponents(song,restored.getPlayerInv().getStackInSlot(53)));
    }

}

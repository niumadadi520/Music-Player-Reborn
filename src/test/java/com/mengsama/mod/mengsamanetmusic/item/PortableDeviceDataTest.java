package com.mengsama.mod.mengsamanetmusic.item;

import com.mengsama.mod.mengsamanetmusic.platform.ItemData;
import com.mengsama.mod.mengsamanetmusic.compat.PortableDevices;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.*;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class PortableDeviceDataTest {
    private static MusicPlayerItem item;
    @BeforeAll static void setup() throws Exception {
        MusicPlayerItemTest.bootstrapMinecraft();
        item=Registry.register(BuiltInRegistries.ITEM,ResourceLocation.fromNamespaceAndPath("mengsama_test", "portable_walkman"),new MusicPlayerItem(Blocks.STONE,new Item.Properties().stacksTo(1)));
    }
    @Test void inventoryTransferCopyRetainsExactDeviceDataAndNewStackIsResolved() {
        ItemStack original=new ItemStack(item);UUID id=MusicPlayerItem.getOrCreateInstanceId(original);
        var playlist=net.minecraft.core.NonNullList.withSize(54,ItemStack.EMPTY);playlist.set(0,new ItemStack(Items.MUSIC_DISC_CAT));
        MusicPlayerItem.saveAllCdsToItem(original,playlist);MusicPlayerItem.setPlay(original,true);MusicPlayerItem.setCurrentTime(original,1200);
        var earbud=new net.minecraft.nbt.CompoundTag();earbud.putString("id","mengsamanetmusic:pink_wired_earbuds_both");earbud.putByte("Count",(byte)1);
        var slots=new net.minecraft.nbt.CompoundTag();slots.put("Slot0",earbud);ItemData.put(original, "EarbudInventory",slots);
        ItemData.putString(original, "ExtensionData","preserve");
        var before=ItemData.nullable(original).copy();ItemStack transferred=original.copy();
        ItemStack found=PortableDevices.match(List.of(new ItemStack(item),transferred),id);
        assertSame(transferred,found);assertEquals(before,ItemData.nullable(found));
        assertTrue(com.mengsama.mod.mengsamanetmusic.earbuds.EarbudSlots.wired(found));
        assertSame(Items.MUSIC_DISC_CAT,MusicPlayerItem.loadAllCds(found).get(0).getItem());
        MusicPlayerItem.tickTime(found);assertEquals(1199,MusicPlayerItem.getCurrentTime(transferred));assertEquals(1200,MusicPlayerItem.getCurrentTime(original));
    }
    @Test void unknownIdentityNeverSelectsAnotherWalkmanOrCreatesANewId() {
        ItemStack first=new ItemStack(item);ItemStack other=new ItemStack(item);UUID id=MusicPlayerItem.getOrCreateInstanceId(other);
        assertTrue(PortableDevices.match(List.of(first),id).isEmpty());assertFalse(ItemData.has(first));
        assertTrue(PortableDevices.match(List.of(first,other),UUID.randomUUID()).isEmpty());
    }
    @Test void copiedUuidOnNonMusicItemCannotBecomeAControllableDevice() {
        ItemStack walkman=new ItemStack(item);UUID id=MusicPlayerItem.getOrCreateInstanceId(walkman);
        ItemStack unrelated=new ItemStack(Items.STICK);ItemData.set(unrelated, ItemData.nullable(walkman).copy());
        assertFalse(PortableDevices.matches(unrelated,id));assertFalse(PortableDevices.matches(walkman,null));
        assertSame(walkman,PortableDevices.match(List.of(unrelated,walkman),id));
    }
}

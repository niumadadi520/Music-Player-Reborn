package com.mengsama.mod.mengsamanetmusic.earbuds;

import com.mengsama.mod.mengsamanetmusic.item.MusicPlayerItem;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class EarbudDeviceSelectionTest {
    private static MusicPlayerItem walkman;
    @BeforeAll static void setup() throws Exception {
        net.minecraft.SharedConstants.tryDetectVersion();
        var field=net.minecraft.server.Bootstrap.class.getDeclaredField("isBootstrapped");
        field.setAccessible(true);field.setBoolean(null,true);
        Class.forName("net.minecraft.core.registries.BuiltInRegistries");
        walkman=Registry.register(BuiltInRegistries.ITEM,new ResourceLocation("mengsama_test","earbud_selection"),
                new MusicPlayerItem(Blocks.STONE,new Item.Properties().stacksTo(1)));
    }
    private static ItemStack device() {
        var stack=new ItemStack(walkman);
        var earbuds=new CompoundTag();earbuds.putString("id","mengsamanetmusic:pink_wired_earbuds_both");earbuds.putByte("Count",(byte)1);
        var slots=new CompoundTag();slots.put("Slot0",earbuds);
        stack.getOrCreateTag().put(EarbudSlots.KEY,slots);
        MusicPlayerItem.getOrCreateInstanceId(stack);
        return stack;
    }
    @Test void removedIdleUpgradeInInventoryDoesNotKeepAConnectionOrLoseItsContents() {
        var removed=device();removed.getOrCreateTag().putString("PlaylistNote","keep");
        var before=removed.save(new CompoundTag());
        assertTrue(EarbudDeviceSelection.choose(removed,ItemStack.EMPTY,ItemStack.EMPTY,ItemStack.EMPTY).isEmpty());
        assertEquals(before,removed.save(new CompoundTag()));assertTrue(EarbudSlots.wired(removed));
    }
    @Test void currentPlaybackAndPauseKeepTheSelectedDeviceInsteadOfAnotherBackpack() {
        var playing=device();var other=device();MusicPlayerItem.setPlay(playing,true);
        assertSame(playing,EarbudDeviceSelection.choose(playing,other,ItemStack.EMPTY,other));
        MusicPlayerItem.setPaused(playing,true);
        assertSame(playing,EarbudDeviceSelection.choose(playing,ItemStack.EMPTY,ItemStack.EMPTY,other));
        MusicPlayerItem.setPlay(playing,false);
        assertSame(other,EarbudDeviceSelection.choose(playing,ItemStack.EMPTY,ItemStack.EMPTY,other));
    }
    @Test void takingDeviceInEitherHandOrReinstallingItAllowsANewConnection() {
        var removed=device();var other=device();
        assertSame(removed,EarbudDeviceSelection.choose(ItemStack.EMPTY,removed,other,other));
        assertSame(removed,EarbudDeviceSelection.choose(ItemStack.EMPTY,ItemStack.EMPTY,removed,other));
        assertSame(removed,EarbudDeviceSelection.choose(ItemStack.EMPTY,ItemStack.EMPTY,ItemStack.EMPTY,removed));
    }
    @Test void EarbudDataOnUnrelatedItemsAndEmptyWalkmenCannotCreateConnections() {
        var forged=new ItemStack(Items.STICK);forged.setTag(device().getTag().copy());MusicPlayerItem.setPlay(forged,true);
        assertTrue(EarbudDeviceSelection.choose(forged,forged,new ItemStack(walkman),forged).isEmpty());
    }
}

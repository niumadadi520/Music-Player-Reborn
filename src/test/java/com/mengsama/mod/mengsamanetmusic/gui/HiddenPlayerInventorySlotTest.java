package com.mengsama.mod.mengsamanetmusic.gui;

import com.mengsama.mod.mengsamanetmusic.platform.ItemData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HiddenPlayerInventorySlotTest {
    @BeforeAll static void bootstrapMinecraft() throws Exception {
        com.mengsama.mod.mengsamanetmusic.testsupport.HeadlessEnvironment.initialize(); net.minecraft.SharedConstants.tryDetectVersion();
        var bootstrapped = net.minecraft.server.Bootstrap.class.getDeclaredField("isBootstrapped");
        bootstrapped.setAccessible(true);
        bootstrapped.setBoolean(null, true);
        Class.forName("net.minecraft.core.registries.BuiltInRegistries");
    }

    @Test void hiddenSlotKeepsTheOriginalStackAndNbtButRejectsAllPlayerInteraction() {
        var inventory = new SimpleContainer(36);
        var item = new ItemStack(Items.DIAMOND, 42);
        ItemData.putString(item, "custom_owner_note", "必须保留");
        inventory.setItem(8, item);
        CompoundTag before = ((net.minecraft.nbt.CompoundTag)item.saveOptional(com.mengsama.mod.mengsamanetmusic.platform.GameRegistries.lookup()));
        var slot = new HiddenPlayerInventorySlot(inventory, 8);

        assertFalse(slot.isActive());
        assertTrue(slot.x < 0 && slot.y < 0);
        assertFalse(slot.mayPlace(new ItemStack(Items.STONE)));
        assertFalse(slot.mayPickup(null));
        assertSame(item, slot.getItem());
        assertEquals(before, ((net.minecraft.nbt.CompoundTag)inventory.getItem(8).saveOptional(com.mengsama.mod.mengsamanetmusic.platform.GameRegistries.lookup())));
        assertEquals(8, slot.getContainerSlot());

        var synchronizedCopy = item.copy();
        synchronizedCopy.setCount(40);
        slot.set(synchronizedCopy);
        assertSame(synchronizedCopy, inventory.getItem(8), "vanilla authoritative slot synchronization remains enabled");
        assertEquals("必须保留", ItemData.get(slot.getItem()).getString("custom_owner_note"));
    }
}

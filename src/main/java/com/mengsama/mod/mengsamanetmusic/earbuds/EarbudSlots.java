package com.mengsama.mod.mengsamanetmusic.earbuds;

import com.mengsama.mod.mengsamanetmusic.platform.ItemData;
import com.mengsama.mod.mengsamanetmusic.item.MusicPlayerItem;
import com.mengsama.mod.mengsamanetmusic.compat.HeadphonesAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.player.Player;

 
public final class EarbudSlots {
    public static final String KEY = "EarbudInventory";
    private EarbudSlots() {}
    public static ItemStack get(ItemStack device, int slot) {
        if (slot < 0 || slot > 2 || !ItemData.has(device)) return ItemStack.EMPTY;
        return com.mengsama.mod.mengsamanetmusic.platform.StoredItems.read(com.mengsama.mod.mengsamanetmusic.platform.GameRegistries.lookup(), ItemData.nullable(device).getCompound(KEY).getCompound("Slot" + slot));
    }
    public static boolean accepts(int slot, ItemStack item) {
        return item.isEmpty() || item.getItem() instanceof EarbudItem e && e.kind == slot;
    }
    public static void set(ItemStack device, int slot, ItemStack item) {
        if (slot < 0 || slot > 2 || !accepts(slot, item) || item.getCount() > 1) throw new IllegalArgumentException("Invalid earbud slot");
        CompoundTag data = ItemData.get(device).getCompound(KEY).copy();
        if (item.isEmpty()) data.remove("Slot" + slot); else data.put("Slot" + slot, item.save(com.mengsama.mod.mengsamanetmusic.platform.GameRegistries.lookup()));
        ItemData.put(device, KEY, data);
    }
    public static int mask(ItemStack device) {
        if (present(device,0)) return 3;
        return (present(device,1) ? 1 : 0) | (present(device,2) ? 2 : 0);
    }
    private static boolean present(ItemStack device,int slot) {
        if(!ItemData.has(device)||!ItemData.nullable(device).contains(KEY,10))return false;
        CompoundTag slots=ItemData.nullable(device).getCompound(KEY);
        if(!slots.contains("Slot"+slot,10))return false;
        CompoundTag item=slots.getCompound("Slot"+slot);
        String expected=switch(slot){case 0->"pink_wired_earbuds_both";case 1->"pink_bluetooth_earbuds_left";default->"pink_bluetooth_earbuds_right";};
        return (item.contains("count") ? item.getInt("count") : item.getByte("Count"))>0 && ("mengsamanetmusic:"+expected).equals(item.getString("id"));
    }
    public static boolean installed(ItemStack device) { return device.getItem() instanceof MusicPlayerItem && mask(device) != 0; }
    public static boolean wired(ItemStack device) { return present(device,0); }
    public static void initializeCase(ItemStack box,ItemStack left,ItemStack right) {
        if(ItemData.get(box).getBoolean("EarbudCaseInitialized"))return;
        if(!accepts(1,left)||!accepts(2,right))throw new IllegalArgumentException("Invalid case contents");
        set(box,1,left);set(box,2,right);ItemData.putBoolean(box, "EarbudCaseInitialized",true);
    }
    public static boolean canListen(Player player, ItemStack device) { return installed(device) || HeadphonesAccess.isWearing(player); }
    public static double range(boolean wired) { return wired ? 4 : 32; }
    public static boolean inRange(boolean wired, double distanceSquared) { return Double.isFinite(distanceSquared) && distanceSquared >= 0 && distanceSquared <= range(wired) * range(wired); }
    public static boolean nearLimit(boolean wired, double distanceSquared) { double warning = range(wired) * .8; return inRange(wired, distanceSquared) && distanceSquared >= warning * warning; }
}

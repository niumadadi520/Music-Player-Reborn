package com.mengsama.mod.mengsamanetmusic.compat.backpack;

import com.mengsama.mod.mengsamanetmusic.api.SongInfo;
import com.mengsama.mod.mengsamanetmusic.gui.MusicPlayerMenu;
import com.mengsama.mod.mengsamanetmusic.item.MusicListItem;
import com.mengsama.mod.mengsamanetmusic.item.MusicPlayerItem;
import com.mengsama.mod.mengsamanetmusic.network.PlaybackRefreshSessions;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.*;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

import net.p3pp3rf1y.sophisticatedbackpacks.backpack.wrapper.BackpackWrapper;
import net.p3pp3rf1y.sophisticatedbackpacks.backpack.BackpackBlockEntity;
import net.p3pp3rf1y.sophisticatedbackpacks.common.gui.BackpackContainer;
import net.p3pp3rf1y.sophisticatedbackpacks.util.PlayerInventoryProvider;
import net.p3pp3rf1y.sophisticatedcore.api.IStorageWrapper;
import java.util.*;

public final class BackpackIntegration {
    private static final Map<String, WalkmanUpgradeWrapper> SESSIONS = new HashMap<>();
    private BackpackIntegration() {}
    public static void register() {
        NeoForge.EVENT_BUS.addListener(BackpackIntegration::tick);
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent event) -> SESSIONS.clear());
    }
    static void track(WalkmanUpgradeWrapper wrapper) {
        WalkmanUpgradeWrapper old = SESSIONS.put(wrapper.key(), wrapper);
        if (old != null && old != wrapper) wrapper.adopt(old);
    }
    static WalkmanUpgradeWrapper current(WalkmanUpgradeWrapper wrapper) { return SESSIONS.get(wrapper.key()); }
    static void untrack(WalkmanUpgradeWrapper wrapper) { SESSIONS.values().removeIf(value -> value == wrapper); }
    private static void tick(ServerTickEvent.Post event) {
        
        var it = SESSIONS.values().iterator();
        while (it.hasNext()) {
            var device = it.next();
            if (device.level == null || device.level.getServer().isStopped()) { it.remove(); continue; }
            if (device.level.getServer().getTickCount() - device.lastTick > 10 || !physical(device)) {
                it.remove(); device.detach();
            }
        }
    }
    public static ItemStack earbudDevice(Player player) {
        ItemStack[] result = {ItemStack.EMPTY};
        PlayerInventoryProvider.get().runOnBackpacks(player, (backpack, handler, identifier, slot) -> {
            BackpackWrapper.fromExistingData(backpack).ifPresent(wrapper -> {
                var upgrades=wrapper.getUpgradeHandler();
                for(int i=0;i<upgrades.getSlots();i++) if(com.mengsama.mod.mengsamanetmusic.earbuds.EarbudSlots.installed(upgrades.getStackInSlot(i))) {result[0]=upgrades.getStackInSlot(i);break;}
            }); return !result[0].isEmpty();
        }); return result[0];
    }
    public static ItemStack find(Player player, UUID instance) {
        if (instance == null || player == null) return ItemStack.EMPTY;
        ItemStack[] result = {ItemStack.EMPTY};
        PlayerInventoryProvider.get().runOnBackpacks(player, (backpack, handler, identifier, slot) -> {
            BackpackWrapper.fromExistingData(backpack).ifPresent(wrapper -> {
                var upgrades = wrapper.getUpgradeHandler();
                for (int i = 0; i < upgrades.getSlots(); i++) {
                    var item = upgrades.getStackInSlot(i);
                    if (item.getItem() instanceof WalkmanUpgradeItem && instance.equals(MusicPlayerItem.getInstanceId(item))) { result[0] = item; return; }
                }
                 
                if (player.level().isClientSide) for (var item : wrapper.getRenderInfo().getUpgradeItems())
                    if (item.getItem() instanceof WalkmanUpgradeItem && instance.equals(MusicPlayerItem.getInstanceId(item))) { result[0] = item; return; }
            });
            return !result[0].isEmpty();
        });
        return result[0];
    }
    public static int slot(IStorageWrapper storage) {
        var upgrades = storage.getUpgradeHandler();
        for (int i = 0; i < upgrades.getSlots(); i++) if (upgrades.getStackInSlot(i).getItem() instanceof WalkmanUpgradeItem) return i;
        return -1;
    }
    static WalkmanUpgradeWrapper wrapper(IStorageWrapper storage, int slot) {
        if (slot < 0 || slot >= storage.getUpgradeHandler().getSlots()) return null;
        var upgrade = storage.getUpgradeHandler().getSlotWrappers().get(slot);
        return upgrade instanceof WalkmanUpgradeWrapper walkman ? walkman : null;
    }
    public static void open(ServerPlayer player, int id, int slot) {
        if (!(player.containerMenu instanceof BackpackContainer backpack) || backpack.containerId != id || !backpack.stillValid(player)) return;
        var storage = backpack.getStorageWrapper();
        var upgrade = wrapper(storage, slot);
        if (upgrade == null || storage.getContentsUuid().isEmpty()) return;
        var binding = new BackpackBinding(backpack.getBackpackContext(), storage.getContentsUuid().get(),
                slot, MusicPlayerItem.getOrCreateInstanceId(upgrade.stack), upgrade.stack.copy());
        if (binding.resolve(player).isEmpty()) return;
        binding.attach(player);
        player.openMenu(new SimpleMenuProvider((window, inv, p) -> MusicPlayerMenu.forBackpack(window, inv, binding),
                Component.translatable("gui.mengsamanetmusic.backpack_music")), buffer -> {
            buffer.writeByte(MusicPlayerMenu.Context.BACKPACK.ordinal()); binding.write(buffer);
        });
    }
    static boolean physical(WalkmanUpgradeWrapper wrapper) {
        if (wrapper.retired || wrapper.level == null || wrapper.level.getServer().isStopped()) return false;
        if (wrapper.carrier != null) return wrapper.carrier.isAlive() && !wrapper.carrier.isRemoved()
                && wrapper.carrier.level() == wrapper.level && find(wrapper.carrier, MusicPlayerItem.getInstanceId(wrapper.stack)) == wrapper.stack;
        if (wrapper.position == null || !wrapper.level.hasChunkAt(wrapper.position)) return false;
        var entity = wrapper.level.getBlockEntity(wrapper.position);
        if (!(entity instanceof BackpackBlockEntity backpack)) return false;
        var storage = backpack.getBackpackWrapper();
        if (!storage.getContentsUuid().equals(wrapper.storage.getContentsUuid())) return false;
        var upgrades = storage.getUpgradeHandler();
        for (int i = 0; i < upgrades.getSlots(); i++) if (upgrades.getStackInSlot(i) == wrapper.stack) return true;
        return false;
    }
    public static boolean validBlock(Level level, BlockPos pos, String target) {
        if (level == null || pos == null || !level.hasChunkAt(pos) || !(level.getBlockEntity(pos) instanceof BackpackBlockEntity backpack)) return false;
        var storage = backpack.getBackpackWrapper();
        var id = storage.getContentsUuid().orElse(null);
        if (id == null) return false;
        for (var item : storage.getRenderInfo().getUpgradeItems()) {
            UUID instance = MusicPlayerItem.getInstanceId(item);
            if (item.getItem() instanceof WalkmanUpgradeItem && instance != null && target.equals(WalkmanUpgradeWrapper.blockTarget(level, pos, id, instance))) return true;
        }
        int slot = slot(storage);
        if (slot < 0) return false;
        UUID instance = MusicPlayerItem.getInstanceId(storage.getUpgradeHandler().getStackInSlot(slot));
        return instance != null && target.equals(WalkmanUpgradeWrapper.blockTarget(level, pos, id, instance));
    }
    public static boolean refresh(ServerPlayer sender, BlockPos pos, String target, long generation, long nonce, SongInfo requested) {
        for (var device : SESSIONS.values()) {
            if (!Objects.equals(device.target, target)) continue;
            if (device.level != sender.level() || !physical(device) || !MusicPlayerItem.isPlay(device.stack)) return true;
            if (device.carrier != null && device.carrier != sender || device.carrier == null && (!Objects.equals(pos, device.position)
                    || sender.distanceToSqr(pos.getX(), pos.getY(), pos.getZ()) > 96 * 96)) return true;
            var song = MusicListItem.getSongInfo(MusicPlayerItem.getCurrentCd(device.stack));
            if (song != null && song.sameIdentity(requested) && PlaybackRefreshSessions.consume(target, generation, nonce, song,
                    device.carrier == null ? target : sender.getUUID().toString())) device.play(song, 0, MusicPlayerItem.isPaused(device.stack), nonce);
            return true;
        }
        return false;
    }
}

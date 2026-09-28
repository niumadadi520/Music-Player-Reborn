package com.mengsama.mod.mengsamanetmusic.gui;

import com.mengsama.mod.mengsamanetmusic.api.SongInfo;
import com.mengsama.mod.mengsamanetmusic.compat.EntityMusicDevice;
import com.mengsama.mod.mengsamanetmusic.init.ModMenuTypes;
import com.mengsama.mod.mengsamanetmusic.item.MusicListItem;
import com.mengsama.mod.mengsamanetmusic.item.MusicPlayerItem;
import com.mengsama.mod.mengsamanetmusic.network.ModNetwork;
import com.mengsama.mod.mengsamanetmusic.network.StopMusicPacketClient;
import com.mengsama.mod.mengsamanetmusic.util.PlayMode;
import net.minecraft.core.NonNullList;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class MusicPlayerMenu extends AbstractContainerMenu {
    public static final MenuType<MusicPlayerMenu> TYPE = IForgeMenuType.create(MusicPlayerMenu::fromNetwork);

    public enum Context { PLAYER_HAND, MAID, BACKPACK }
    private final com.mengsama.mod.mengsamanetmusic.compat.BackpackAccess.Binding backpackBinding;
    public com.mengsama.mod.mengsamanetmusic.compat.BackpackAccess.Binding getBackpackBinding() { return backpackBinding; }
    public boolean supportsEarbudSlots() {
        return com.mengsama.mod.mengsamanetmusic.item.MusicPlayerItem.supportsEarbuds(device)
                && (context == Context.PLAYER_HAND || context == Context.BACKPACK && backpackBinding != null && backpackBinding.portable());
    }
    public static MusicPlayerMenu forBackpack(int id, Inventory inv, com.mengsama.mod.mengsamanetmusic.compat.BackpackAccess.Binding binding) {
        return new MusicPlayerMenu(id, inv, Context.BACKPACK, null, -1, null, binding);
    }

    private static MusicPlayerMenu fromNetwork(int windowId, Inventory inv, FriendlyByteBuf data) {
        if (data == null) return forPlayerHand(windowId, inv, null);
        int contextId = data.readUnsignedByte();
        if (contextId == Context.BACKPACK.ordinal()) return forBackpack(windowId, inv, com.mengsama.mod.mengsamanetmusic.compat.BackpackAccess.read(data, inv.player));
        if (contextId == Context.MAID.ordinal()) {
            return forMaid(windowId, inv, data.readUUID(), data.readInt(), data.readUUID());
        }
        return forPlayerHand(windowId, inv, data.readUUID());
    }

    public static final int BUTTON_PLAY = 0;
    public static final int BUTTON_STOP = 1;
    public static final int BUTTON_NEXT = 2;
    public static final int BUTTON_PREV = 3;
    public static final int BUTTON_MODE = 4;
    public static final int BUTTON_BROADCAST = 5;
     
     
    public static final int BUTTON_SELECT_BASE = 10;
    public static final int BUTTON_DELETE_BASE = 64;

    private final Player owner;
    private final Context context;
    private ItemStack device;
    private final UUID boundEntityId;
    private final int boundRuntimeEntityId;
    private final UUID boundInstanceId;

    public static MusicPlayerMenu forPlayerHand(int windowId, Inventory playerInventory, UUID instanceId) {
        return new MusicPlayerMenu(windowId, playerInventory, Context.PLAYER_HAND, null, -1, instanceId);
    }

    public static MusicPlayerMenu forMaid(int windowId, Inventory playerInventory, UUID maidId,
                                           int runtimeEntityId, UUID instanceId) {
        return new MusicPlayerMenu(windowId, playerInventory, Context.MAID, maidId, runtimeEntityId, instanceId);
    }

    private MusicPlayerMenu(int windowId, Inventory playerInventory, Context context, UUID boundEntityId,
                            int boundRuntimeEntityId, UUID boundInstanceId) {
        this(windowId, playerInventory, context, boundEntityId, boundRuntimeEntityId, boundInstanceId, null);
    }

    private MusicPlayerMenu(int windowId, Inventory playerInventory, Context context, UUID boundEntityId,
                            int boundRuntimeEntityId, UUID boundInstanceId, com.mengsama.mod.mengsamanetmusic.compat.BackpackAccess.Binding binding) {
        super(ModMenuTypes.MUSIC_PLAYER.get(), windowId);
        this.backpackBinding = binding;
        this.owner = playerInventory.player;
        this.context = context;
        this.boundEntityId = boundEntityId;
        this.boundRuntimeEntityId = boundRuntimeEntityId;
        this.boundInstanceId = boundInstanceId;
        this.device = context == Context.BACKPACK ? (binding == null ? ItemStack.EMPTY : binding.resolve(owner))
                : context == Context.PLAYER_HAND ? resolveHeldInstance(owner, boundInstanceId)
                : EntityMusicDevice.resolve(owner, boundEntityId, boundRuntimeEntityId, boundInstanceId);

        for (int col = 0; col < 9; col++) {
            this.addSlot(new HiddenPlayerInventorySlot(playerInventory, col));
        }

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new HiddenPlayerInventorySlot(playerInventory, col + row * 9 + 9));
            }
        }
    }

    public int getPlayIndex() {
        Player player = getPlayer();
        if (player != null) {
            ItemStack item = device;
            if (!item.isEmpty()) {
                return MusicPlayerItem.getPlayIndex(item);
            }
        }
        return 0;
    }

    public PlayMode getPlayMode() {
        Player player = getPlayer();
        if (player != null) {
            ItemStack item = device;
            if (!item.isEmpty()) {
                return MusicPlayerItem.getPlayMode(item);
            }
        }
        return PlayMode.SEQUENTIAL;
    }

    public boolean isPlaying() {
        Player player = getPlayer();
        if (player != null) {
            ItemStack item = device;
            if (!item.isEmpty()) {
                return MusicPlayerItem.isPlay(item);
            }
        }
        return false;
    }

    public boolean isBroadcast() {
        return !device.isEmpty() && (MusicPlayerItem.isBroadcast(device)
                || (context == Context.PLAYER_HAND && !canUseHeadphones()));
    }

    public boolean canUseHeadphones() {
        return (context == Context.PLAYER_HAND || backpackBinding != null && backpackBinding.portable())
                && com.mengsama.mod.mengsamanetmusic.earbuds.EarbudSlots.canListen(owner, device);
    }

    public boolean isPlayerHandContext() {
        return context != Context.MAID;
    }

    public List<SongInfo> getPlaylist() {
        List<SongInfo> playlist = new ArrayList<>();
        Player player = getPlayer();
        if (player != null) {
            ItemStack item = device;
            if (!item.isEmpty()) {
                NonNullList<ItemStack> cds = MusicPlayerItem.loadAllCds(item);
                for (int i = 0; i < cds.size(); i++) {
                    ItemStack cd = cds.get(i);
                    if (!cd.isEmpty()) {
                        SongInfo info = getSongInfoFromCd(cd);
                        if (info != null) {
                            playlist.add(info);
                        }
                    }
                }
            }
        }
        return playlist;
    }

    public SongInfo getSongInfo(int index) {
        Player player = getPlayer();
        if (player != null) {
            ItemStack item = device;
            if (!item.isEmpty()) {
                NonNullList<ItemStack> cds = MusicPlayerItem.loadAllCds(item);
                if (index >= 0 && index < cds.size()) {
                    ItemStack cd = cds.get(index);
                    if (!cd.isEmpty()) {
                        return getSongInfoFromCd(cd);
                    }
                }
            }
        }
        return null;
    }

    private SongInfo getSongInfoFromCd(ItemStack cd) {
        return cd.getItem() instanceof MusicListItem ? MusicListItem.getSongInfo(cd) : null;
    }

    public String getTargetId() {
        if (backpackBinding != null) return backpackBinding.target(owner);
        if (device.isEmpty()) return "missing-item";
        if (boundEntityId != null && owner.level() != null) {
            var living = owner.level().getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class,
                    owner.getBoundingBox().inflate(128.0), e -> e.getUUID().equals(boundEntityId)).stream().findFirst().orElse(null);
            if (living != null) return EntityMusicDevice.targetId(living, device);
        }
        return MusicPlayerItem.targetId(owner, device);
    }

    private void sendStop(ServerPlayer player) {
        if (backpackBinding != null) { backpackBinding.stop(player); return; }
        String targetId = getTargetId();
        if (boundEntityId == null) {
             
             
            if (MusicPlayerItem.isBroadcast(device)) {
                ModNetwork.sendToNearby(player.serverLevel(), player.blockPosition(),
                        new StopMusicPacketClient(targetId));
            } else {
                com.mengsama.mod.mengsamanetmusic.earbuds.EarbudSessions.sendPrivate(player, device, new StopMusicPacketClient(targetId));
            }
        } else {
            var entity = player.serverLevel().getEntity(boundRuntimeEntityId);
            if (entity != null) {
                ModNetwork.sendToNearby(player.serverLevel(), entity.blockPosition(), new StopMusicPacketClient(targetId));
                com.mengsama.mod.mengsamanetmusic.compat.MaidMusicAccess.stopLyrics(entity);
            }
        }
    }

    private void sendPause(ServerPlayer player, boolean paused) {
        if (backpackBinding != null) { backpackBinding.pause(player, paused); return; }
        String targetId = getTargetId();
        long generation = com.mengsama.mod.mengsamanetmusic.item.MusicPlayerItem.currentRequestGeneration(
                boundEntityId == null ? player : player.serverLevel().getEntity(boundRuntimeEntityId));
        var packet = new com.mengsama.mod.mengsamanetmusic.network.PauseMusicPacketClient(targetId, paused, generation);
        if (boundEntityId == null) {
            if (MusicPlayerItem.isBroadcast(device)) ModNetwork.sendToNearby(player.serverLevel(), player.blockPosition(), packet);
            else com.mengsama.mod.mengsamanetmusic.earbuds.EarbudSessions.sendPrivate(player, device, packet);
        } else {
            var entity = player.serverLevel().getEntity(boundRuntimeEntityId);
            if (entity != null) ModNetwork.sendToNearby(player.serverLevel(), entity.blockPosition(), packet);
        }
    }

    public void stopPlayback(ServerPlayer player) {
        ItemStack stack = resolveValidatedDevice(player);
        if (stack.isEmpty()) return;
        MusicPlayerItem.setPlay(stack, false);
        MusicPlayerItem.setCurrentTime(stack, 0);
        com.mengsama.mod.mengsamanetmusic.network.PlaybackRefreshSessions.release(getTargetId());
        sendStop(player);
        syncAuthoritativeState(player);
    }

    public void removeSong(ServerPlayer player, int index) {
        ItemStack stack = resolveValidatedDevice(player);
        if (stack.isEmpty()) return;
        boolean current = index == MusicPlayerItem.getPlayIndex(stack);
        if (!MusicPlayerItem.removePlaylistEntry(stack, index)) return;
        if (current) {
            com.mengsama.mod.mengsamanetmusic.network.PlaybackRefreshSessions.release(getTargetId());
            sendStop(player);
        }
        syncAuthoritativeState(player);
    }

    public void playSelected(ItemStack stack, SongInfo info, ServerPlayer player) {
        playToClient(stack, info, player);
    }

    private void playToClient(ItemStack stack, SongInfo info, ServerPlayer player) {
        if (backpackBinding != null) { backpackBinding.play(player, info, 0, false); return; }
        if (boundEntityId == null) {
            MusicPlayerItem.setPlayToClient(stack, info, player);
        } else {
            var entity = player.serverLevel().getEntity(boundEntityId);
            if (entity instanceof net.minecraft.world.entity.LivingEntity living)
                MusicPlayerItem.setPlayToEntity(stack, info, living);
        }
    }

    private Player getPlayer() {
        for (Slot slot : this.slots) {
            if (slot.container instanceof Inventory inv) {
                return inv.player;
            }
        }
        return null;
    }

    @Override
    public boolean clickMenuButton(@NotNull Player player, int buttonId) {
        if (player.level().isClientSide) return true;
        if (!(player instanceof ServerPlayer sp)) return true;

        ItemStack playerItem = resolveValidatedDevice(player);
        if (playerItem.isEmpty()) {
            com.mengsama.mod.mengsamanetmusic.MengSamaNetMusic.LOGGER.warn("Rejected stale music menu action player={} container={}", player.getUUID(), containerId);
            return true;
        }

        switch (buttonId) {
            case BUTTON_PLAY -> {
                if (MusicPlayerItem.isPlay(playerItem)) {
                    boolean paused = !MusicPlayerItem.isPaused(playerItem);
                     
                    MusicPlayerItem.setPaused(playerItem, paused);
                    sendPause(sp, paused);
                } else {
                    ItemStack currentCd = MusicPlayerItem.getCurrentCd(playerItem);
                    if (currentCd.isEmpty()) return true;
                    SongInfo info = getSongInfoFromCd(currentCd);
                    if (info != null && info.songUrl != null && !info.songUrl.isEmpty()) {
                        playToClient(playerItem, info, sp);
                    }
                }
            }
            case BUTTON_STOP -> {
                stopPlayback(sp);
            }
            case BUTTON_NEXT -> {
                MusicPlayerItem.setPlay(playerItem, false);
                sendStop(sp);
                MusicPlayerItem.advanceToNext(playerItem);
                ItemStack currentCd = MusicPlayerItem.getCurrentCd(playerItem);
                if (currentCd.isEmpty()) return true;
                SongInfo info = getSongInfoFromCd(currentCd);
                if (info != null && info.songUrl != null && !info.songUrl.isEmpty()) {
                    playToClient(playerItem, info, sp);
                }
            }
            case BUTTON_PREV -> {
                MusicPlayerItem.setPlay(playerItem, false);
                sendStop(sp);
                int currentIndex = MusicPlayerItem.getPlayIndex(playerItem);
                NonNullList<ItemStack> cds = MusicPlayerItem.loadAllCds(playerItem);
                int prevIndex = currentIndex - 1;
                while (prevIndex >= 0 && cds.get(prevIndex).isEmpty()) prevIndex--;
                if (prevIndex < 0) {
                    for (int i = cds.size() - 1; i >= 0; i--) {
                        if (!cds.get(i).isEmpty()) { prevIndex = i; break; }
                    }
                }
                if (prevIndex >= 0) {
                    MusicPlayerItem.setPlayIndex(playerItem, prevIndex);
                    ItemStack cd = MusicPlayerItem.getCurrentCd(playerItem);
                    if (!cd.isEmpty()) {
                        SongInfo info = getSongInfoFromCd(cd);
                        if (info != null && info.songUrl != null && !info.songUrl.isEmpty()) {
                            playToClient(playerItem, info, sp);
                        }
                    }
                }
            }
            case BUTTON_MODE -> {
                PlayMode currentMode = MusicPlayerItem.getPlayMode(playerItem);
                MusicPlayerItem.setPlayMode(playerItem, currentMode.getNext());
            }
            case BUTTON_BROADCAST -> {
                if (com.mengsama.mod.mengsamanetmusic.earbuds.EarbudSlots.installed(playerItem)) { sp.displayClientMessage(net.minecraft.network.chat.Component.literal("耳机已连接，强制单独听歌"), true); return true; }
                if (backpackBinding != null) backpackBinding.broadcast(sp, !MusicPlayerItem.isBroadcast(playerItem));
                if (context == Context.PLAYER_HAND) {
                    if (!com.mengsama.mod.mengsamanetmusic.earbuds.EarbudSlots.canListen(sp, playerItem)) {
                        sp.displayClientMessage(net.minecraft.network.chat.Component.literal("佩戴耳机后才能关闭外放"), true);
                        MusicPlayerItem.changeBroadcast(playerItem, sp, true);
                    } else {
                        MusicPlayerItem.changeBroadcast(playerItem, sp, !MusicPlayerItem.isBroadcast(playerItem));
                    }
                }
            }
            default -> {
                if (buttonId >= BUTTON_DELETE_BASE) {
                    removeSong(sp, buttonId - BUTTON_DELETE_BASE);
                } else if (buttonId >= BUTTON_SELECT_BASE) {
                    int index = buttonId - BUTTON_SELECT_BASE;
                    NonNullList<ItemStack> cds = MusicPlayerItem.loadAllCds(playerItem);
                    if (index >= 0 && index < cds.size() && !cds.get(index).isEmpty()) {
                        MusicPlayerItem.setPlay(playerItem, false);
                        sendStop(sp);
                        MusicPlayerItem.setPlayIndex(playerItem, index);
                        ItemStack cd = MusicPlayerItem.getCurrentCd(playerItem);
                        if (!cd.isEmpty()) {
                            SongInfo info = getSongInfoFromCd(cd);
                            if (info != null && info.songUrl != null && !info.songUrl.isEmpty()) {
                                playToClient(playerItem, info, sp);
                            }
                        }
                    }
                }
            }
        }
        syncAuthoritativeState(sp);
        return true;
    }

     
    @Override
    public void clicked(int slotId, int button, @NotNull ClickType clickType, @NotNull Player player) {
         
    }

    @Override
    public @NotNull ItemStack quickMoveStack(@NotNull Player player, int index) {
        return ItemStack.EMPTY;
    }

    public ItemStack getDevice() { return device; }
    public void applyAuthoritativeTag(net.minecraft.nbt.CompoundTag tag) {
        if (!device.isEmpty()) device.setTag(tag.copy());
    }
    public void syncAuthoritativeState(ServerPlayer player) {
        if (backpackBinding != null) backpackBinding.save(player);
        if (!device.isEmpty()) {
            net.minecraft.nbt.CompoundTag tag = device.getTag() == null ? new net.minecraft.nbt.CompoundTag() : device.getTag().copy();
            ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                    new com.mengsama.mod.mengsamanetmusic.network.MaidDeviceSyncPacket(containerId, tag));
            broadcastChanges();
        }
    }
    public UUID getBoundEntityId() { return boundEntityId; }
    public int getBoundRuntimeEntityId() { return boundRuntimeEntityId; }
    public UUID getBoundInstanceId() { return boundInstanceId; }

    private static ItemStack resolveHeldInstance(Player player, UUID instanceId) {
        return com.mengsama.mod.mengsamanetmusic.compat.PortableDevices.controllable(player, instanceId);
    }

     
    public ItemStack resolveValidatedDevice(Player player) {
        ItemStack resolved = context == Context.BACKPACK ? (backpackBinding == null ? ItemStack.EMPTY : backpackBinding.resolve(player))
                : context == Context.PLAYER_HAND
                ? resolveHeldInstance(player, boundInstanceId)
                : EntityMusicDevice.resolve(player, boundEntityId, boundRuntimeEntityId, boundInstanceId);
        if (resolved.isEmpty()) return ItemStack.EMPTY;
        if (context == Context.MAID) {
            var entity = player.level().getEntity(boundRuntimeEntityId);
            if (entity == null || !entity.getUUID().equals(boundEntityId)
                    || !com.mengsama.mod.mengsamanetmusic.compat.MaidMusicAccess.mayControl(player, entity)) return ItemStack.EMPTY;
        }
        device = resolved;
        return resolved;
    }

    @Override
    public boolean stillValid(@NotNull Player player) {
        return !resolveValidatedDevice(player).isEmpty();
    }
}

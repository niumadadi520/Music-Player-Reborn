package com.mengsama.mod.mengsamanetmusic.karaoke;

import com.mengsama.mod.mengsamanetmusic.platform.ItemData;
import com.mengsama.mod.mengsamanetmusic.block.MusicPlayerBlockEntity;
import com.mengsama.mod.mengsamanetmusic.init.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.world.item.ItemStack;
import com.mengsama.mod.mengsamanetmusic.util.PlayMode;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

 
public final class KaraokeBlockEntity extends MusicPlayerBlockEntity {
    private UUID deviceId;
    private final Set<UUID> connections = new LinkedHashSet<>();
    private int volume = 80;
    private boolean microphoneActive;
    private long voiceUntil;
    private long lastVoiceSync = Long.MIN_VALUE / 2;
    private CompoundTag itemExtras = new CompoundTag();
    private ItemStack standItem = ItemStack.EMPTY;
    private ItemStack mountedMicrophone = ItemStack.EMPTY;
    private boolean transferring;
    private boolean dropsReleased;
    private boolean lifecycleSuppressed;
    private boolean pendingLoad;
    private boolean lifecycleDetached;
    public KaraokeBlockEntity(BlockPos pos, BlockState state) { super(ModBlockEntities.KARAOKE_DEVICE.get(), pos, state); }
    public boolean isSpeaker() { return getBlockState().getBlock() instanceof KaraokeDeviceBlock b && b.isSpeaker(); }
    public boolean isStand() { return getBlockState().getBlock() instanceof KaraokeDeviceBlock b && b.isStand(); }
    public boolean hasMicrophone() { return !isSpeaker() && (!isStand() || !mountedMicrophone.isEmpty()); }
    public String modelName() { return isStand() ? hasMicrophone() ? "pink_microphone_stand_loaded" : "pink_microphone_stand"
            : ((KaraokeDeviceBlock)getBlockState().getBlock()).modelName(); }
    public UUID deviceId() {
        if (isStand() && !hasMicrophone()) return null;
        if (deviceId == null && level != null && !level.isClientSide) {
            deviceId = isSpeaker() ? UUID.randomUUID() : KaraokeData.get(level.getServer()).create();
            markDirty();
        }
        return deviceId;
    }
     
    UUID cachedDeviceId() { return deviceId; }
    public void setDeviceId(UUID id) {
        deviceId = isStand() && !hasMicrophone() ? null : id;
        if (!mountedMicrophone.isEmpty() && deviceId != null) ItemData.putUUID(mountedMicrophone, KaraokeMicrophoneItem.ID_TAG, deviceId);
        markDirty();
    }
    public Set<UUID> connections() { return Set.copyOf(connections); }
    public int volume() { return volume; }
    public CompoundTag itemExtras() { return itemExtras.copy(); }
    public void setItemExtras(CompoundTag tag) { itemExtras = tag.copy(); itemExtras.remove("KaraokeBlockData"); }
    public void setVolume(int value) { volume = KaraokeCode.volume(value); markDirty(); }
    public boolean connect(UUID id) {
        if (connections.size() >= KaraokeCode.MAX_CONNECTIONS && !connections.contains(id)) return false;
        boolean added = connections.add(id); if (added) markDirty(); return added;
    }
    public boolean disconnect(UUID id) { boolean removed = connections.remove(id); if (removed) markDirty(); return removed; }
    public void setMicrophoneActive(boolean active) {
        active &= hasMicrophone();
        if (microphoneActive != active) { microphoneActive = active; markDirty(); }
    }
     
    public ItemStack mountedMicrophone() {
        return isStand() && hasMicrophone() ? KaraokeMicrophoneData.snapshot(mountedMicrophone, deviceId,
                getPlayerInv(), getPlayIndex(), getPlayMode()) : ItemStack.EMPTY;
    }
    public ItemStack standItem() { return KaraokeMicrophoneData.standOnly(standItem); }
    public void setStandItem(ItemStack source) {
        standItem = KaraokeMicrophoneData.standOnly(source);
        itemExtras = ItemData.has(standItem) ? ItemData.nullable(standItem).copy() : new CompoundTag();
    }
     
    public void mountMicrophone(ItemStack microphone) {
        if (!isStand() || hasMicrophone() || !KaraokeMicrophoneData.isHandheld(microphone)
                || KaraokeMicrophoneItem.getId(microphone) == null) throw new IllegalArgumentException("Invalid stand microphone transfer");
        var songs = KaraokeMicrophoneData.playlist(microphone);
        boolean previousTransfer = transferring;
        transferring = true;
        try {
            mountedMicrophone = microphone.copy(); mountedMicrophone.setCount(1);
            deviceId = KaraokeMicrophoneItem.getId(mountedMicrophone);
            for (int i = 0; i < getPlayerInv().getSlots(); i++) getPlayerInv().setStackInSlot(i, i < songs.size() ? songs.get(i).copy() : ItemStack.EMPTY);
            setPlayIndex(KaraokeMicrophoneData.playIndex(microphone)); setPlayMode(KaraokeMicrophoneData.playMode(microphone));
            setPlay(false); setPaused(false); setCurrentTime(0); microphoneActive = false; voiceUntil = 0;
             
            super.setBlockState(getBlockState());
        } finally { transferring = previousTransfer; }
        markDirty();
    }
     
    public void clearMicrophone() {
        if (!isStand()) return;
        boolean previousTransfer = transferring;
        transferring = true;
        try {
            mountedMicrophone = ItemStack.EMPTY; deviceId = null; microphoneActive = false; voiceUntil = 0;
            connections.clear(); volume = 80;
            for (int i = 0; i < getPlayerInv().getSlots(); i++) getPlayerInv().setStackInSlot(i, ItemStack.EMPTY);
            setPlay(false); setPaused(false); setCurrentTime(0); setPlayIndex(0); setPlayMode(PlayMode.SEQUENTIAL);
            super.setBlockState(getBlockState());
        } finally { transferring = previousTransfer; }
        markDirty();
    }
    boolean claimDrops() { if (dropsReleased) return false; dropsReleased = true; return true; }
    @Override public void markDirty() {
         
        if (!transferring && !lifecycleSuppressed && !isRemoved()) super.markDirty();
    }
    @Override public net.neoforged.neoforge.items.IItemHandler automationInventory() {
        return isStand() && !hasMicrophone() ? null : super.automationInventory();
    }
    public void voicePulse() {
        if (level == null) return;
        voiceUntil = level.getGameTime() + 12;
        if (level.getGameTime() - lastVoiceSync >= 6) {
            lastVoiceSync = level.getGameTime();
            KaraokeNetwork.pulse(this, voiceUntil);
        }
    }
    public void acceptVoicePulse(long until) { voiceUntil = until; }
    @Override public void saveAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (deviceId != null && (!isStand() || hasMicrophone())) tag.putUUID("KaraokeDeviceId", deviceId);
        else tag.remove("KaraokeDeviceId");
        ListTag list = new ListTag(); connections.forEach(id -> list.add(StringTag.valueOf(id.toString())));
        tag.put("KaraokeConnections", list); tag.putInt("KaraokeVolume", volume);
        tag.put("KaraokeItemExtras", itemExtras.copy());
        if (isStand()) {
            if (!standItem.isEmpty()) tag.put("KaraokeStandItem", KaraokeMicrophoneData.standOnly(standItem).save(com.mengsama.mod.mengsamanetmusic.platform.GameRegistries.lookup()));
            else tag.remove("KaraokeStandItem");
            if (!mountedMicrophone.isEmpty()) tag.put("KaraokeMountedMicrophone", mountedMicrophone().save(com.mengsama.mod.mengsamanetmusic.platform.GameRegistries.lookup()));
            else tag.remove("KaraokeMountedMicrophone");
        }
         
    }
    @Override public void loadAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        deviceId = tag.hasUUID("KaraokeDeviceId") ? tag.getUUID("KaraokeDeviceId") : null;
        connections.clear();
        for (var entry : tag.getList("KaraokeConnections", 8)) {
            UUID id = KaraokeCode.parse(entry.getAsString());
            if (id != null && connections.size() < KaraokeCode.MAX_CONNECTIONS) connections.add(id);
        }
        volume = tag.contains("KaraokeVolume") ? KaraokeCode.volume(tag.getInt("KaraokeVolume")) : 80;
        itemExtras = tag.getCompound("KaraokeItemExtras").copy();
        if (isStand()) {
            standItem = KaraokeMicrophoneData.standOnly(com.mengsama.mod.mengsamanetmusic.platform.StoredItems.read(com.mengsama.mod.mengsamanetmusic.platform.GameRegistries.lookup(), tag.getCompound("KaraokeStandItem")));
            ItemStack savedMicrophone = com.mengsama.mod.mengsamanetmusic.platform.StoredItems.read(com.mengsama.mod.mengsamanetmusic.platform.GameRegistries.lookup(), tag.getCompound("KaraokeMountedMicrophone"));
            mountedMicrophone = KaraokeMicrophoneData.isHandheld(savedMicrophone) ? savedMicrophone.copy() : ItemStack.EMPTY;
            if (!mountedMicrophone.isEmpty()) {
                mountedMicrophone.setCount(1);
                UUID storedId = KaraokeMicrophoneItem.getId(mountedMicrophone);
                if (storedId != null) deviceId = storedId;
                else if (deviceId != null) ItemData.putUUID(mountedMicrophone, KaraokeMicrophoneItem.ID_TAG, deviceId);
            } else {
                transferring = true;
                try { clearMicrophone(); } finally { transferring = false; }
            }
        }
        microphoneActive = tag.getBoolean("KaraokeRuntimeActive"); voiceUntil = tag.getLong("KaraokeVoiceUntil");
        if (!hasMicrophone() && !isSpeaker()) { microphoneActive = false; voiceUntil = 0; }
    }
    @Override public CompoundTag getUpdateTag(net.minecraft.core.HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        tag.putBoolean("KaraokeRuntimeActive", microphoneActive); tag.putLong("KaraokeVoiceUntil", voiceUntil);
        return tag;
    }
    @Override public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide) {
            lifecycleSuppressed = true; lifecycleDetached = false;
             
            KaraokeServer.stopDeviceForUnload(this);
            microphoneActive = false; voiceUntil = 0;
            pendingLoad = true;
            KaraokeServer.deferDeviceLoad(this);
        }
    }
     
    public boolean tickLifecycle() {
        if (!pendingLoad) return !lifecycleDetached;
        if (!(level instanceof net.minecraft.server.level.ServerLevel serverLevel) || !serverLevel.getServer().isRunning()
                || serverLevel.getServer().isStopped()) return false;
        var chunk = serverLevel.getChunkSource().getChunkNow(worldPosition.getX() >> 4, worldPosition.getZ() >> 4);
        if (chunk == null || chunk.getBlockEntity(worldPosition, net.minecraft.world.level.chunk.LevelChunk.EntityCreationType.CHECK) != this) return false;
        pendingLoad = false; lifecycleSuppressed = false;
        if (isStand()) KaraokeStandPlacement.ensureTop(level, worldPosition);
        KaraokeServer.registerDevice(this);
        return true;
    }
    private void detachLifecycle() {
        if (lifecycleDetached) return;
        lifecycleDetached = true; lifecycleSuppressed = true; pendingLoad = false;
        if (level != null && !level.isClientSide) KaraokeServer.stopDeviceForUnload(this);
        microphoneActive = false; voiceUntil = 0;
    }
    @Override public void onChunkUnloaded() {
        detachLifecycle();
        super.onChunkUnloaded();
    }
    @Override public void setRemoved() {
        detachLifecycle();
        super.setRemoved();
    }
    @Override public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "karaoke", 2, state -> {
            boolean active = isSpeaker() ? (level != null && voiceUntil > level.getGameTime()) || isPlay() && !isPaused() : hasMicrophone() && microphoneActive;
            String animation = active ? (isSpeaker() ? "playing" : "recording") : "idle";
            return state.setAndContinue(RawAnimation.begin().thenLoop("animation." + modelName() + "." + animation));
        }));
    }
}

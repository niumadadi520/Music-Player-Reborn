package com.mengsama.mod.mengsamanetmusic.block;

import com.mengsama.mod.mengsamanetmusic.MengSamaNetMusic;
import com.mengsama.mod.mengsamanetmusic.api.SongInfo;
import com.mengsama.mod.mengsamanetmusic.item.MusicListItem;
import com.mengsama.mod.mengsamanetmusic.item.MusicPlayerItem;
import com.mengsama.mod.mengsamanetmusic.network.*;
import com.mengsama.mod.mengsamanetmusic.util.PlayMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.*;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.*;
import java.util.concurrent.ThreadLocalRandom;

 
public abstract class MusicDeviceEntity extends BlockEntity implements IMusicPlayerBlockEntity {
    protected final DevicePlaylist playlist = new DevicePlaylist(this::markDirty);
    private final DevicePlaybackClock clock = new DevicePlaybackClock();
    private LazyOptional<IItemHandler> inventoryView;
    private volatile long request;
    protected MusicDeviceEntity(BlockEntityType<?> type, BlockPos position, BlockState state) { super(type, position, state); }

    @Override public void load(CompoundTag data) { super.load(data); playlist.read(data); clock.read(data); }
    @Override public void saveAdditional(CompoundTag data) { super.saveAdditional(data); playlist.write(data); clock.write(data); }
    @Override public CompoundTag getUpdateTag() { return saveWithoutMetadata(); }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
    @Override public ItemStackHandler getPlayerInv() { return playlist.inventory(); }
    @Override public ItemStack getCurrentCd() { return playlist.current(); }
    @Override public int getPlayIndex() { return playlist.selection(); }
    @Override public void setPlayIndex(int index) { playlist.select(index); }
    @Override public PlayMode getPlayMode() { return playlist.order(); }
    @Override public void setPlayMode(PlayMode mode) { playlist.order(mode); }
    @Override public void advanceToNext() { playlist.advance(bound -> ThreadLocalRandom.current().nextInt(bound)); }
    @Override public boolean isPlay() { return clock.playing(); }
    @Override public boolean isPaused() { return clock.paused(); }
    @Override public void setPlay(boolean value) { clock.playing(value); markDirty(); }
    @Override public void setPaused(boolean value) { clock.paused(value); markDirty(); }
    @Override public int getCurrentTime() { return clock.remaining(); }
    @Override public void setCurrentTime(int ticks) { clock.remaining(ticks); }
    public void tickTime() { clock.decrement(); }
    public boolean hasSignal() { return clock.powered(); }
    public void setSignal(boolean value) { clock.powered(value); }
    public long currentRequestGeneration() { return request; }
    @Override public String blockTargetId() { return "block:" + getLevel().dimension().location() + ":" + getBlockPos().asLong(); }

    public void powerChanged(boolean powered) {
        if (clock.powered() == powered || !(level instanceof ServerLevel)) return;
        clock.powered(powered);
        markDirty();
        if (powered) playSelected();
    }
    protected boolean allowsPlayback() { return true; }
    protected final void playSelected() {
        SongInfo selected = MusicListItem.getSongInfo(getCurrentCd());
        if (selected != null) setPlayToClient(selected); else stopForTransfer();
    }
    protected final void tickPlayback(boolean repeat) {
        if (!(level instanceof ServerLevel) || !clock.advance()) return;
        if (!repeat) { setPlay(false); return; }
        advanceToNext();
        playSelected();
    }
    @Override public void setPlayToClient(SongInfo song) { start(song, 0, 0, false); }
    public void setPlayToClient(SongInfo song, long nonce) { start(song, nonce, 0, false); }
    @Override public void setPlayToClient(SongInfo song, int second) { start(song, 0, second, false); }
    @Override public void seekToClient(SongInfo song, int second, boolean paused) { start(song, 0, second, paused); }

    private void start(SongInfo song, long nonce, int second, boolean paused) {
        if (!(level instanceof ServerLevel world) || song == null || isRemoved()) return;
        if (!allowsPlayback()) { stopForTransfer(); return; }
        SongInfo identity = song.clone();
        long generation = PlaybackGenerations.next();
        request = generation;
        clock.playing(true);
        clock.remaining(0);
        clock.paused(paused);
        markDirty();
        MusicPlayerItem.resolveUrlAsync(identity).whenCompleteAsync((resolved, error) -> {
            if (isRemoved() || request != generation || !clock.playing()) return;
            if (error != null || !MusicPlayerItem.hasPlayableUrl(resolved)) {
                stopForTransfer();
                MengSamaNetMusic.LOGGER.warn("Device playback could not resolve an audio source: {}", blockTargetId(), error);
                return;
            }
            try {
                SongInfo stable = MusicPlayerItem.stableSongInfo(resolved, identity);
                MusicListItem.setSongInfo(stable, getCurrentCd());
                int offset = Math.max(0, Math.min(resolved.songTime, second));
                clock.remaining((int)Math.min(Integer.MAX_VALUE, Math.max(1L, (resolved.songTime - (long)offset) * 20 + 64)));
                String target = blockTargetId();
                PlaybackRefreshSessions.publish(target, generation, stable, target);
                ModNetwork.sendToNearby(world, worldPosition, new PlayMusicPacket(worldPosition, target,
                        resolved.songUrl, stable.rawUrl, resolved.songTime, resolved.songName, generation, nonce, resolved, offset));
                if (clock.paused()) ModNetwork.sendToNearby(world, worldPosition, new PauseMusicPacketClient(target, true, generation));
                markDirty();
            } catch (RuntimeException failure) {
                stopForTransfer();
                MengSamaNetMusic.LOGGER.warn("Device playback could not publish its result", failure);
            }
        }, world.getServer());
    }

    public void stopForTransfer() { stopForUnload(); markDirty(); }
     
    public void stopForUnload() {
        request = PlaybackGenerations.next();
        clock.clear();
        if (!(level instanceof ServerLevel world)) return;
        String target = blockTargetId();
        PlaybackRefreshSessions.release(target);
        if (world.getServer().isRunning() && !world.getServer().isStopped())
            ModNetwork.sendToNearby(world, worldPosition, new StopMusicPacketClient(target));
    }
    @Override public void markDirty() {
        setChanged();
        if (level == null || isRemoved()) return;
        BlockState state = getBlockState();
        level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_ALL);
    }

    private void releaseInventoryView() {
        LazyOptional<IItemHandler> previous = inventoryView;
        inventoryView = null;
        if (previous != null) previous.invalidate();
    }
    @Override public <T> LazyOptional<T> getCapability(Capability<T> capability, Direction side) {
        if (capability != ForgeCapabilities.ITEM_HANDLER || isRemoved()) return super.getCapability(capability, side);
        if (inventoryView == null) inventoryView = LazyOptional.of(playlist::inventory);
        return inventoryView.cast();
    }
    @Override public void invalidateCaps() { releaseInventoryView(); super.invalidateCaps(); }
    @Override public void setBlockState(BlockState state) { releaseInventoryView(); super.setBlockState(state); }
}

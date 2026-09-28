package com.mengsama.mod.mengsamanetmusic.compat.backpack;

import com.mengsama.mod.mengsamanetmusic.platform.ItemData;
import com.mengsama.mod.mengsamanetmusic.api.SongInfo;
import com.mengsama.mod.mengsamanetmusic.compat.HeadphonesAccess;
import com.mengsama.mod.mengsamanetmusic.item.MusicListItem;
import com.mengsama.mod.mengsamanetmusic.item.MusicPlayerItem;
import com.mengsama.mod.mengsamanetmusic.network.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;
import net.p3pp3rf1y.sophisticatedcore.api.IStorageWrapper;
import net.p3pp3rf1y.sophisticatedcore.upgrades.IUpgradeWrapper;
import net.p3pp3rf1y.sophisticatedcore.upgrades.ITickableUpgrade;
import java.util.UUID;
import java.util.function.Consumer;

public final class WalkmanUpgradeWrapper implements IUpgradeWrapper, ITickableUpgrade {
    final IStorageWrapper storage;
    final ItemStack stack;
    private final Consumer<ItemStack> saver;
    ServerLevel level;
    ServerPlayer carrier;
    BlockPos position;
    UUID controller;
    String target;
    long generation;
    long lastTick = Long.MIN_VALUE;
    long lastProcessedTick = Long.MIN_VALUE;
    boolean initialized;
    boolean retired;

    public WalkmanUpgradeWrapper(IStorageWrapper storage, ItemStack stack, Consumer<ItemStack> saver) {
        this.storage = storage; this.stack = stack; this.saver = saver;
    }
    @Override public ItemStack getUpgradeStack() { return stack; }
    @Override public boolean isEnabled() { return true; }
    @Override public boolean canBeDisabled() { return false; }
    @Override public void setEnabled(boolean enabled) {}
    @Override public boolean hideSettingsTab() { return true; }
    @Override public void onBeforeRemoved() { detach(); }

    void detach() {
        if (retired) return;
         
        stop(false);
        retired = true;
        BackpackIntegration.untrack(this);
        if (level != null) com.mengsama.mod.mengsamanetmusic.earbuds.EarbudSessions.detachDevice(
                level.getServer(), MusicPlayerItem.getInstanceId(stack));
    }

    String key() { return storage.getContentsUuid().orElse(new UUID(0, 0)) + ":" + MusicPlayerItem.getOrCreateInstanceId(stack); }
    public void save() {
        if (retired) return;
         
        var handler = storage.getUpgradeHandler();
        for (int i = 0; i < handler.getSlots(); i++) if (handler.getStackInSlot(i) == stack) {
            saver.accept(stack);
            if (level != null && target != null && level.getServer().isRunning() && !level.getServer().isStopped()) {
                for (var viewer : level.getServer().getPlayerList().getPlayers()) {
                    if (viewer.containerMenu instanceof com.mengsama.mod.mengsamanetmusic.gui.MusicPlayerMenu menu
                            && menu.getBackpackBinding() != null && target.equals(menu.getTargetId())
                            && menu.resolveValidatedDevice(viewer) == stack) {
                        ModNetwork.CHANNEL.sendToPlayer(viewer, new MaidDeviceSyncPacket(menu.containerId, ItemData.get(stack).copy()));
                    }
                }
            }
            return;
        }
    }
    void adopt(WalkmanUpgradeWrapper previous) {
        level = previous.level; carrier = previous.carrier; position = previous.position;
        controller = previous.controller; target = previous.target; generation = previous.generation;
        initialized = previous.initialized; lastTick = previous.lastTick; lastProcessedTick = previous.lastProcessedTick; previous.retired = true;
    }
    void attach(ServerLevel world, ServerPlayer owner, BlockPos pos) {
        if (retired) return;
        BackpackIntegration.track(this);
        if (initialized && (world != level || owner != carrier || owner == null && !pos.equals(position))) stop();
        if (!initialized) {
            if (owner != null && MusicPlayerItem.isPlay(stack)) {
                MusicPlayerItem.stopForTransfer(stack, owner);
                ModNetwork.sendToNearby(world, pos, new StopMusicPacketClient("device:" + MusicPlayerItem.getOrCreateInstanceId(stack)));
            }
            MusicPlayerItem.setPlay(stack, false);
            MusicPlayerItem.setPaused(stack, false);
            MusicPlayerItem.setCurrentTime(stack, 0);
            ItemData.remove(stack, "AutoAdvanceArmed");
            storage.getUpgradeHandler().setRenderUpgradeItems();
        }
        level = world; carrier = owner; position = pos.immutable(); initialized = true;
        target = owner == null ? blockTarget(world, pos, storage.getContentsUuid().orElseThrow(), MusicPlayerItem.getOrCreateInstanceId(stack))
                : "item:" + owner.getUUID() + ":-1:" + MusicPlayerItem.getOrCreateInstanceId(stack);
        lastTick = world.getServer().getTickCount();
    }
    static String blockTarget(Level level, BlockPos pos, UUID backpack, UUID device) {
        return "backpack:" + level.dimension().location() + ":" + pos.asLong() + ":" + backpack + ":" + device;
    }
    @Override public void tick(Entity entity, Level world, BlockPos pos) {
        if (retired || !(world instanceof ServerLevel serverLevel) || world.getServer().isStopped()) return;
        if (entity != null && !(entity instanceof ServerPlayer)) return;
        attach(serverLevel, entity instanceof ServerPlayer p ? p : null, pos);
        if (lastProcessedTick == world.getServer().getTickCount()) return;
        lastProcessedTick = world.getServer().getTickCount();
        if (carrier != null && !com.mengsama.mod.mengsamanetmusic.earbuds.EarbudSlots.canListen(carrier, stack) && !MusicPlayerItem.isBroadcast(stack)) broadcast(true);
        if (!MusicPlayerItem.isPlay(stack) || MusicPlayerItem.isPaused(stack)) return;
        MusicPlayerItem.tickTime(stack);
        if (MusicPlayerItem.getCurrentTime(stack) == 0 && ItemData.get(stack).getBoolean("AutoAdvanceArmed")) {
            ItemData.putBoolean(stack, "AutoAdvanceArmed", false);
            MusicPlayerItem.advanceToNext(stack);
            SongInfo next = MusicListItem.getSongInfo(MusicPlayerItem.getCurrentCd(stack));
            if (next == null) stop(); else play(next, 0, false, 0);
        } else if (world.getGameTime() % 20 == 0) save();
    }
    public void play(SongInfo source, int second, boolean paused, long nonce) {
        if (!initialized || retired || level == null || !BackpackIntegration.physical(this)) return;
        if (carrier == null && com.mengsama.mod.mengsamanetmusic.earbuds.EarbudSlots.installed(stack)) { stop(); return; }
        SongInfo requested = source.clone();
        long request = generation = PlaybackGenerations.next();
        String expectedTarget = target;
        MusicPlayerItem.setPlay(stack, true); MusicPlayerItem.setPaused(stack, paused);
         
        MusicPlayerItem.setCurrentTime(stack, 0);
        ItemData.remove(stack, "AutoAdvanceArmed");
        if (carrier == null || !com.mengsama.mod.mengsamanetmusic.earbuds.EarbudSlots.canListen(carrier, stack)) MusicPlayerItem.setBroadcast(stack, true);
        save();
        MusicPlayerItem.resolveUrlAsync(requested).whenCompleteAsync((resolved, error) -> {
            WalkmanUpgradeWrapper current = BackpackIntegration.current(this);
            if (current == null || current.generation != request || !expectedTarget.equals(current.target)
                    || !MusicPlayerItem.isPlay(current.stack) || !BackpackIntegration.physical(current)) return;
            SongInfo selected = MusicListItem.getSongInfo(MusicPlayerItem.getCurrentCd(current.stack));
            if (selected == null || !requested.sameIdentity(selected)) return;
            if (error != null || !MusicPlayerItem.hasPlayableUrl(resolved)) {
                current.stop();
                var listener = current.controller == null ? null : current.level.getServer().getPlayerList().getPlayer(current.controller);
                if (listener != null) listener.displayClientMessage(net.minecraft.network.chat.Component.literal("背包随身听：未能取得可播放音源"), false);
                return;
            }
            SongInfo stable = MusicPlayerItem.stableSongInfo(resolved, requested);
            var cds = MusicPlayerItem.loadAllCds(current.stack);
            MusicListItem.setSongInfo(stable, cds.get(MusicPlayerItem.getPlayIndex(current.stack)));
            MusicPlayerItem.saveAllCdsPreservingSlots(current.stack, cds);
            int start = Math.max(0, Math.min(resolved.songTime, second));
            MusicPlayerItem.setCurrentTime(current.stack, Math.max(1, (resolved.songTime - start) * 20 + 64));
            current.save();
            PlaybackRefreshSessions.publish(expectedTarget, request, stable,
                    current.carrier == null ? expectedTarget : current.carrier.getUUID().toString());
            if (current.carrier != null) current.send(new PlayerPlayMusicPacket(current.carrier.getId(), expectedTarget,
                    resolved.songUrl, resolved.songTime, resolved.songName, MusicPlayerItem.getPlayIndex(current.stack), request, nonce,
                    resolved, false, MusicPlayerItem.isBroadcast(current.stack), start));
            else current.send(new PlayMusicPacket(current.position, expectedTarget, resolved.songUrl, stable.songUrl,
                    resolved.songTime, resolved.songName, request, nonce, resolved, start));
            if (MusicPlayerItem.isPaused(current.stack)) current.send(new PauseMusicPacketClient(expectedTarget, true, request));
        }, level.getServer());
    }
    void send(Object packet) {
        if (level == null || level.getServer().isStopped()) return;
        if (carrier == null || MusicPlayerItem.isBroadcast(stack)) ModNetwork.sendToNearby(level, carrier == null ? position : carrier.blockPosition(), packet);
        else com.mengsama.mod.mengsamanetmusic.earbuds.EarbudSessions.sendPrivate(carrier, stack, packet);
    }
    public void stop() {
        stop(true);
    }
    private void stop(boolean persist) {
        if (retired) return;
        generation = PlaybackGenerations.next();
        if (target != null) { send(new StopMusicPacketClient(target)); PlaybackRefreshSessions.release(target); }
        MusicPlayerItem.setPlay(stack, false); MusicPlayerItem.setPaused(stack, false); MusicPlayerItem.setCurrentTime(stack, 0);
        ItemData.remove(stack, "AutoAdvanceArmed");
        if (persist && level != null && level.getServer().isRunning() && !level.getServer().isStopped()) save();
    }
    public void pause(boolean value) { if (retired) return; MusicPlayerItem.setPaused(stack, value); send(new PauseMusicPacketClient(target, value, generation)); save(); }
    public void broadcast(boolean value) {
        if (retired) return;
        boolean allowed = carrier != null && com.mengsama.mod.mengsamanetmusic.earbuds.EarbudSlots.canListen(carrier, stack);
        boolean next = !com.mengsama.mod.mengsamanetmusic.earbuds.EarbudSlots.installed(stack) && (!allowed || value);
        if (next == MusicPlayerItem.isBroadcast(stack)) return;
        if (target != null) ModNetwork.sendToNearby(level, carrier == null ? position : carrier.blockPosition(), new StopMusicPacketClient(target));
        MusicPlayerItem.setBroadcast(stack, next); save();
        if (MusicPlayerItem.isPlay(stack)) {
            SongInfo song = MusicListItem.getSongInfo(MusicPlayerItem.getCurrentCd(stack));
            if (song != null) play(song, Math.max(0, song.songTime - Math.max(0, MusicPlayerItem.getCurrentTime(stack) - 64) / 20), MusicPlayerItem.isPaused(stack), 0);
        }
    }
}

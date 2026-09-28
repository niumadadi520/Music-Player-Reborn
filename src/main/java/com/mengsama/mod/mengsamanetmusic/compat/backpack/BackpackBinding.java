package com.mengsama.mod.mengsamanetmusic.compat.backpack;

import com.mengsama.mod.mengsamanetmusic.api.SongInfo;
import com.mengsama.mod.mengsamanetmusic.compat.BackpackAccess;
import com.mengsama.mod.mengsamanetmusic.item.MusicPlayerItem;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import net.p3pp3rf1y.sophisticatedbackpacks.common.gui.BackpackContainer;
import net.p3pp3rf1y.sophisticatedbackpacks.common.gui.BackpackContext;
import java.util.UUID;

public final class BackpackBinding implements BackpackAccess.Binding {
    private BackpackContext context;
    private final UUID backpackId;
    private final int slot;
    private final UUID deviceId;
    private final ItemStack snapshot;
    public BackpackBinding(BackpackContext context, UUID backpackId, int slot, UUID deviceId, ItemStack snapshot) {
        this.context = context; this.backpackId = backpackId; this.slot = slot; this.deviceId = deviceId; this.snapshot = snapshot;
    }
    @Override public boolean portable() { return !context.getType().name().startsWith("BLOCK"); }
    private WalkmanUpgradeWrapper upgrade(Player player) {
        if (player.isSpectator() || !context.canInteractWith(player)) return null;
         
        var data = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        try { context.toBuffer(data); context = BackpackContext.fromBuffer(data, player.level()); }
        finally { data.release(); }
        var storage = context.getBackpackWrapper(player);
        if (!storage.getContentsUuid().filter(backpackId::equals).isPresent()) return null;
        var result = BackpackIntegration.wrapper(storage, slot);
        return result != null && deviceId.equals(MusicPlayerItem.getInstanceId(result.stack)) ? result : null;
    }
    @Override public ItemStack resolve(Player player) {
        if (player.level().isClientSide) return snapshot;
        var result = upgrade(player); return result == null ? ItemStack.EMPTY : result.stack;
    }
    void attach(ServerPlayer player) {
        var upgrade = upgrade(player);
        if (upgrade == null) return;
        var owner = context.getOwnerPlayer(player).orElse(player);
        upgrade.attach(player.serverLevel(), portable() && owner instanceof ServerPlayer p ? p : null,
                portable() ? owner.blockPosition() : context.getBackpackPosition(player));
        upgrade.controller = player.getUUID();
        upgrade.save();
    }
    @Override public void save(Player player) { var upgrade = upgrade(player); if (upgrade != null) { upgrade.save(); context.saveBackpackStack(); } }
    @Override public String target(Player player) {
        if (portable()) return "item:" + context.getOwnerPlayer(player).orElse(player).getUUID() + ":-1:" + deviceId;
        return WalkmanUpgradeWrapper.blockTarget(player.level(), context.getBackpackPosition(player), backpackId, deviceId);
    }
    @Override public void play(ServerPlayer player, SongInfo song, int second, boolean paused) {
        attach(player); var upgrade = upgrade(player); if (upgrade != null) upgrade.play(song, second, paused, 0);
    }
    @Override public void stop(ServerPlayer player) { var upgrade = upgrade(player); if (upgrade != null) upgrade.stop(); }
    @Override public void pause(ServerPlayer player, boolean paused) { var upgrade = upgrade(player); if (upgrade != null) upgrade.pause(paused); }
    @Override public void broadcast(ServerPlayer player, boolean broadcast) { var upgrade = upgrade(player); if (upgrade != null) upgrade.broadcast(broadcast); }
    @Override public void write(FriendlyByteBuf buffer) {
        context.toBuffer(buffer); buffer.writeUUID(backpackId); buffer.writeVarInt(slot); buffer.writeUUID(deviceId); ItemStack.OPTIONAL_STREAM_CODEC.encode((net.minecraft.network.RegistryFriendlyByteBuf)buffer,snapshot);
    }
    public static BackpackBinding read(FriendlyByteBuf buffer, Player player) {
        return new BackpackBinding(BackpackContext.fromBuffer(buffer, player.level()), buffer.readUUID(), buffer.readVarInt(), buffer.readUUID(), ItemStack.OPTIONAL_STREAM_CODEC.decode((net.minecraft.network.RegistryFriendlyByteBuf)buffer));
    }
    @Override public void returnToBackpack(ServerPlayer player) {
        if (resolve(player).isEmpty()) return;
        player.openMenu(new SimpleMenuProvider((window, inv, p) -> new BackpackContainer(window, p, context),
                context.getBackpackWrapper(player).getBackpack().getHoverName()), context::toBuffer);
    }
}

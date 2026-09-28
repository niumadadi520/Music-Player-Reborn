package com.mengsama.mod.mengsamanetmusic.compat;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.mengsama.mod.mengsamanetmusic.item.MusicPlayerItem;
import com.mengsama.mod.mengsamanetmusic.network.ModNetwork;
import com.mengsama.mod.mengsamanetmusic.network.StopMusicPacketClient;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.tick.*;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

 
public final class ActiveMaidMusicTracker {
    private static final Map<UUID, ActiveDevice> ACTIVE = new HashMap<>();

    private ActiveMaidMusicTracker() {}

    public static void activate(EntityMaid maid, ItemStack device, String targetId) {
        UUID instanceId = MusicPlayerItem.getOrCreateInstanceId(device);
        com.mengsama.mod.mengsamanetmusic.MengSamaNetMusic.LOGGER.info(
                "[播放阶段] 设备验证通过 maid={} instance={} target={}", maid.getUUID(), instanceId, targetId);
        ActiveDevice replacement = new ActiveDevice(maid.level().dimension(), maid.getUUID(), instanceId, targetId);
        ActiveDevice previous = ACTIVE.put(maid.getUUID(), replacement);
        if (previous != null && !previous.targetId.equals(targetId)) broadcastStop(previous.targetId);
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        
        validate(event.getServer());
    }

    static void validate(MinecraftServer server) {
        Iterator<Map.Entry<UUID, ActiveDevice>> iterator = ACTIVE.entrySet().iterator();
        while (iterator.hasNext()) {
            ActiveDevice active = iterator.next().getValue();
            ServerLevel level = server.getLevel(active.dimension);
            EntityMaid maid = level == null ? null : level.getEntity(active.maidId) instanceof EntityMaid found ? found : null;
            if (!isStillValid(maid, active)) {
                if (maid != null) {
                    ItemStack current = EntityMusicDevice.heldPlayer(maid);
                    if (!current.isEmpty() && MusicPlayerItem.getOrCreateInstanceId(current).equals(active.instanceId)) {
                        MusicPlayerItem.setPlay(current, false);
                    }
                }
                broadcastStop(active.targetId);
                MaidLyricSynchronizer.stop(active.maidId);
                iterator.remove();
            }
        }
    }

    private static boolean isStillValid(EntityMaid maid, ActiveDevice active) {
        if (maid == null || maid.isRemoved() || !maid.isAlive()) return false;
        if (maid.getTask() == null || !TouhouLittleMaidExtension.MUSIC_TASK_UID.equals(maid.getTask().getUid())) return false;
        ItemStack current = EntityMusicDevice.heldPlayer(maid);
        return !current.isEmpty()
                && MusicPlayerItem.isPlay(current)
                && MusicPlayerItem.getOrCreateInstanceId(current).equals(active.instanceId)
                && active.targetId.equals(EntityMusicDevice.targetId(maid, current));
    }

    private static void broadcastStop(String targetId) {
        ModNetwork.CHANNEL.sendToAll( new StopMusicPacketClient(targetId));
    }

    private record ActiveDevice(ResourceKey<Level> dimension, UUID maidId, UUID instanceId, String targetId) {}
}

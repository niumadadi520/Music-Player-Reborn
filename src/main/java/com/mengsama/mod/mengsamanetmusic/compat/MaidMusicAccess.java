package com.mengsama.mod.mengsamanetmusic.compat;

import com.mengsama.mod.mengsamanetmusic.api.SongInfo;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.ModList;

 
public final class MaidMusicAccess {
    private MaidMusicAccess() {}
    private static boolean available() {
        return ModList.get() != null && ModList.get().isLoaded(TouhouLittleMaidCompat.TLM);
    }
    public static boolean isMaid(Entity entity) { return available() && Tlm.isMaid(entity); }
    public static boolean hasMusicTask(Entity entity) { return available() && Tlm.hasMusicTask(entity); }
    public static boolean mayControl(Player viewer, Entity entity) {
        return available() && Tlm.mayControl(viewer, entity);
    }
    public static String ownerKey(Entity entity) { return available() ? Tlm.ownerKey(entity) : ""; }
    public static void activate(LivingEntity entity, ItemStack stack, String target) {
        if (available()) Tlm.activate(entity, stack, target);
    }
    public static void startLyrics(LivingEntity entity, String target, SongInfo song) {
        if (available()) Tlm.startLyrics(entity, target, song);
    }
    public static void stopLyrics(Entity entity) { if (available()) Tlm.stopLyrics(entity); }
    public static void notifyOwner(Entity entity, String reason) { if (available()) Tlm.notifyOwner(entity, reason); }
    public static void openGui(ServerPlayer viewer, Entity entity) { if (mayControl(viewer, entity)) Tlm.openGui(viewer, entity); }

     
    private static final class Tlm {
        static boolean isMaid(Entity entity) {
            return entity instanceof com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
        }
        static boolean hasMusicTask(Entity entity) {
            return entity instanceof com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid maid
                    && maid.getTask() != null && TouhouLittleMaidExtension.MUSIC_TASK_UID.equals(maid.getTask().getUid());
        }
        static boolean mayControl(Player viewer, Entity entity) {
            return entity instanceof com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid maid
                    && maid.isOwnedBy(viewer) && maid.isAlive() && !maid.isSleeping()
                    && viewer.level() == maid.level() && viewer.distanceToSqr(maid) < 25.0 && hasMusicTask(maid);
        }
        static String ownerKey(Entity entity) {
            return entity instanceof com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid maid && maid.getOwner() != null
                    ? maid.getOwner().getUUID().toString() : "";
        }
        static void activate(LivingEntity entity, ItemStack stack, String target) {
            if (entity instanceof com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid maid)
                ActiveMaidMusicTracker.activate(maid, stack, target);
        }
        static void startLyrics(LivingEntity entity, String target, SongInfo song) {
            if (entity instanceof com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid maid)
                MaidLyricSynchronizer.start(maid, target, song);
        }
        static void stopLyrics(Entity entity) { if (isMaid(entity)) MaidLyricSynchronizer.stop(entity.getUUID()); }
        static void notifyOwner(Entity entity, String reason) {
            if (entity instanceof com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid maid
                    && maid.getOwner() instanceof ServerPlayer owner)
                owner.sendSystemMessage(net.minecraft.network.chat.Component.literal("女仆音乐播放失败：" + reason));
        }
        static void openGui(ServerPlayer viewer, Entity entity) {
            ((com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid) entity).openMaidGui(viewer, 0);
        }
    }
}

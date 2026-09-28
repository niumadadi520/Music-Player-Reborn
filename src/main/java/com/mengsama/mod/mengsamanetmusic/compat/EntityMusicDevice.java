package com.mengsama.mod.mengsamanetmusic.compat;

import com.mengsama.mod.mengsamanetmusic.item.MusicPlayerItem;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.UUID;

 
public final class EntityMusicDevice {
    private EntityMusicDevice() {}

    public static ItemStack heldPlayer(LivingEntity entity) {
        ItemStack main = entity.getMainHandItem();
        if (main.getItem() instanceof MusicPlayerItem) return main;
        ItemStack off = entity.getOffhandItem();
        return off.getItem() instanceof MusicPlayerItem ? off : ItemStack.EMPTY;
    }

     
    public static ItemStack playbackDevice(LivingEntity entity, String targetId) {
        UUID expected = PlaybackTargetId.instanceId(targetId);
        if (expected == null) return ItemStack.EMPTY;
        if (entity instanceof Player player) {
            if (targetId == null || !targetId.startsWith("item:" + player.getUUID() + ":")) return ItemStack.EMPTY;
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                ItemStack candidate = player.getInventory().getItem(i);
                if (candidate.getItem() instanceof MusicPlayerItem && expected.equals(MusicPlayerItem.getInstanceId(candidate))) return candidate;
            }
            return PortableDevices.find(player, expected, false);
        }
        ItemStack held = heldPlayer(entity);
        return !held.isEmpty() && expected.equals(MusicPlayerItem.getInstanceId(held))
                && targetId.equals(targetId(entity, held)) ? held : ItemStack.EMPTY;
    }

    public static ItemStack resolve(Player viewer, UUID entityUuid, int entityId, UUID instanceId) {
        if (viewer.level() == null) return ItemStack.EMPTY;
        var entity = viewer.level().getEntity(entityId);
        if (!(entity instanceof LivingEntity living) || !living.getUUID().equals(entityUuid)) return ItemStack.EMPTY;
        if (!MaidMusicAccess.hasMusicTask(living)) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = heldPlayer(living);
        if (stack.isEmpty()) return ItemStack.EMPTY;
        return MusicPlayerItem.getOrCreateInstanceId(stack).equals(instanceId) ? stack : ItemStack.EMPTY;
    }

     
    public static ItemStack resolve(Player viewer, UUID entityUuid, UUID instanceId) {
        if (viewer.level() == null) return ItemStack.EMPTY;
        LivingEntity living = viewer.level().getEntitiesOfClass(LivingEntity.class,
                viewer.getBoundingBox().inflate(128.0), e -> e.getUUID().equals(entityUuid)).stream().findFirst().orElse(null);
        return living == null ? ItemStack.EMPTY : resolve(viewer, entityUuid, living.getId(), instanceId);
    }

    public static String targetId(LivingEntity entity, ItemStack stack) {
        if (entity instanceof Player player) return MusicPlayerItem.targetId(player, stack);
        ResourceLocation dimension = entity.level().dimension().location();
        return "maid:" + dimension + ":" + entity.getUUID() + ":" + MusicPlayerItem.getOrCreateInstanceId(stack);
    }
}

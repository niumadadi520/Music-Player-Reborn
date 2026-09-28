package com.mengsama.mod.mengsamanetmusic.compat.backpack.charm;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import org.slf4j.Logger;

import java.util.Iterator;
import java.util.Map;
import java.util.WeakHashMap;

 
public final class BackpackCharmRenderer {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<LivingEntity, Tracked> TRACKED = new WeakHashMap<>();
    private static final double ANCHOR_X = 4.25, ANCHOR_Y = 5.55, ANCHOR_Z = 1.8;
    private static final double BATTERY_OFFSET = 1.15;
    private static CharmMesh mesh;
    private static Level trackedLevel;

    private BackpackCharmRenderer() {}

    public static void reload(ResourceManager resources) {
        try {
            mesh = CharmMesh.load(resources);
        } catch (Exception exception) {
            mesh = null;
            LOGGER.error("Could not load pink walkman backpack charm; backpack rendering remains available", exception);
        }
        clear();
    }

    public static void clear() {
        TRACKED.clear();
        trackedLevel = null;
    }

    public static void render(LivingEntity entity, PoseStack stack, MultiBufferSource buffers, int light, boolean hasBattery, java.util.UUID device) {
        if (mesh == null || entity.isInvisible()) {
            return;
        }
        if (trackedLevel != entity.level()) {
            clear();
            trackedLevel = entity.level();
        }
        Tracked tracked = TRACKED.computeIfAbsent(entity, ignored -> new Tracked());
        double outwardOffset = hasBattery ? BATTERY_OFFSET : 0;
        if (tracked.extraOutwardOffsetBlocks != outwardOffset / 16.0) {
            tracked.extraOutwardOffsetBlocks = outwardOffset / 16.0;
            tracked.dynamics.reset();
            tracked.initialized = false;
            tracked.lastSampleTick = Long.MIN_VALUE;
        }
        tracked.lastRenderedTick = entity.level().getGameTime();
        if (!tracked.initialized) {
            tracked.tick(entity);
            tracked.initialized = true;
        }
        Minecraft minecraft = Minecraft.getInstance();
        CharmDynamics.Pose pose = tracked.dynamics.interpolated(minecraft.isPaused() ? 1.0 : minecraft.getFrameTime());
        stack.pushPose();
         
         
        stack.translate((ANCHOR_X - 8.0) / 16.0, (ANCHOR_Y - 8.0) / 16.0, (ANCHOR_Z - outwardOffset - 8.0) / 16.0);
        stack.scale(1.0F / 16.0F, 1.0F / 16.0F, 1.0F / 16.0F);
        mesh.render(stack, buffers.getBuffer(RenderType.entityCutoutNoCull(mesh.texture)), light, pose);
        com.mengsama.mod.mengsamanetmusic.earbuds.client.EarbudRender.captureBackpackPlug(device, stack, pose.chainPitch(), pose.chainRoll(), pose.pendantPitch(), pose.pendantYaw(), pose.pendantRoll());
        stack.popPose();
    }

    public static void clientTick(TickEvent.ClientTickEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (event.phase != TickEvent.Phase.END || minecraft.isPaused()) {
            return;
        }
        if (minecraft.level == null || minecraft.level != trackedLevel) {
            clear();
            return;
        }
        long now = minecraft.level.getGameTime();
        Iterator<Map.Entry<LivingEntity, Tracked>> iterator = TRACKED.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<LivingEntity, Tracked> entry = iterator.next();
            LivingEntity entity = entry.getKey();
            Tracked tracked = entry.getValue();
            if (entity.isRemoved() || entity.level() != minecraft.level || now - tracked.lastRenderedTick > 100) {
                iterator.remove();
            } else {
                tracked.tick(entity);
            }
        }
    }

    private static final class Tracked {
        private final CharmDynamics dynamics = new CharmDynamics();
        private boolean initialized;
        private long lastRenderedTick;
        private double extraOutwardOffsetBlocks;
        private long lastSampleTick = Long.MIN_VALUE;

        private void tick(LivingEntity entity) {
            long tick = entity.level().getGameTime();
            if (lastSampleTick == tick) {
                return;
            }
            lastSampleTick = tick;
            dynamics.sample(tick, entity.getX(), entity.getY(), entity.getZ(), entity.yBodyRot,
                    entity.onGround(), entity.isCrouching(), extraOutwardOffsetBlocks);
        }
    }
}

package com.mengsama.mod.mengsamanetmusic.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoBlockRenderer;

import java.util.Map;
import java.util.WeakHashMap;

 
public class LodMusicBlockRenderer<T extends BlockEntity & GeoAnimatable> implements BlockEntityRenderer<T> {
    private final GeoBlockRenderer<T> full;
    private final GeoBlockRenderer<T> medium;
    private final GeoBlockRenderer<T> far;
    private final Map<T, ModelDetailPolicy.Detail> lastDetail = new WeakHashMap<>();

    public LodMusicBlockRenderer(GeoModel<T> full, GeoModel<T> medium, GeoModel<T> far) {
        this.full = new GeoBlockRenderer<>(full);
        this.medium = new GeoBlockRenderer<>(medium);
        this.far = new StaticRenderer<>(far);
    }

    @Override public net.minecraft.world.phys.AABB getRenderBoundingBox(T block) {
        return block instanceof com.mengsama.mod.mengsamanetmusic.block.MusicDeviceEntity device
                ? device.getRenderBoundingBox() : new net.minecraft.world.phys.AABB(block.getBlockPos());
    }

    @Override
    public void render(T block, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        var pos = block.getBlockPos();
        double distance = camera.distanceToSqr(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5);
        ModelDetailPolicy.Detail detail = ModelDetailPolicy.select(distance, lastDetail.get(block));
        lastDetail.put(block, detail);
        GeoBlockRenderer<T> renderer = switch (detail) {
            case FULL -> full;
            case MEDIUM -> medium;
            case FAR -> far;
        };
        renderer.render(block, partialTick, pose, buffers, light, overlay);
    }

     
    static final class StaticRenderer<T extends BlockEntity & GeoAnimatable> extends GeoBlockRenderer<T> {
        StaticRenderer(GeoModel<T> model) { super(model); }

        @Override public void updateAnimatedTextureFrame(T block) {
             
        }

        @Override
        public void actuallyRender(PoseStack pose, T block, BakedGeoModel model, RenderType renderType,
                MultiBufferSource buffers, VertexConsumer buffer, boolean isReRender, float partialTick,
                int light, int overlay, int color) {
            if (!isReRender) {
                pose.translate(.5, 0, .5);
                rotateBlock(getFacing(block), pose);
            }
             
            super.actuallyRender(pose, block, model, renderType, buffers, buffer, true, partialTick,
                    light, overlay, color);
        }
    }
}

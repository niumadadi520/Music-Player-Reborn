package com.mengsama.mod.mengsamanetmusic.karaoke.client;

import com.mengsama.mod.mengsamanetmusic.MengSamaNetMusic;
import com.mengsama.mod.mengsamanetmusic.client.renderer.LodMusicBlockRenderer;
import com.mengsama.mod.mengsamanetmusic.karaoke.KaraokeBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

import java.util.Map;

 
public final class KaraokeBlockRenderer implements BlockEntityRenderer<KaraokeBlockEntity> {
    private final Map<String, LodMusicBlockRenderer<KaraokeBlockEntity>> renderers = Map.of(
            "pink_microphone", renderer("pink_microphone"),
            "pink_handheld_microphone", renderer("pink_handheld_microphone"),
            "pink_microphone_stand", renderer("pink_microphone_stand"),
            "pink_microphone_stand_loaded", renderer("pink_microphone_stand_loaded"),
            "pink_speaker", renderer("pink_speaker"));

    public KaraokeBlockRenderer(BlockEntityRendererProvider.Context context) { }

    private static LodMusicBlockRenderer<KaraokeBlockEntity> renderer(String name) {
        return new LodMusicBlockRenderer<>(new DeviceModel(name, ""),
                new DeviceModel(name, "_medium"), new DeviceModel(name, "_far"));
    }

    @Override
    public void render(KaraokeBlockEntity block, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int light, int overlay) {
        LodMusicBlockRenderer<KaraokeBlockEntity> renderer = renderers.get(block.modelName());
        if (renderer != null) renderer.render(block, partialTick, pose, buffers, light, overlay);
    }

    private static final class DeviceModel extends GeoModel<KaraokeBlockEntity> {
        private final ResourceLocation geometry;
        private final ResourceLocation texture;
        private final ResourceLocation animation;

        private DeviceModel(String name, String suffix) {
            geometry = new ResourceLocation(MengSamaNetMusic.MOD_ID, "geo/" + name + suffix + ".geo.json");
            texture = new ResourceLocation(MengSamaNetMusic.MOD_ID, "textures/block/" + name + ".png");
            animation = new ResourceLocation(MengSamaNetMusic.MOD_ID, "animations/" + name + ".animation.json");
        }

        @Override public ResourceLocation getModelResource(KaraokeBlockEntity block) { return geometry; }
        @Override public ResourceLocation getTextureResource(KaraokeBlockEntity block) { return texture; }
        @Override public ResourceLocation getAnimationResource(KaraokeBlockEntity block) { return animation; }
    }
}

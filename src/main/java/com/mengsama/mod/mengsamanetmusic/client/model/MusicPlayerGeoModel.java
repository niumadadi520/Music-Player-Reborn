package com.mengsama.mod.mengsamanetmusic.client.model;

import com.mengsama.mod.mengsamanetmusic.MengSamaNetMusic;
import com.mengsama.mod.mengsamanetmusic.block.MusicPlayerBlockEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public class MusicPlayerGeoModel extends GeoModel<MusicPlayerBlockEntity> {
    private static final ResourceLocation MODEL = new ResourceLocation(MengSamaNetMusic.MOD_ID, "geo/rose_gramophone.geo.json");
    private static final ResourceLocation TEXTURE = new ResourceLocation(MengSamaNetMusic.MOD_ID, "textures/block/rose_gramophone.png");
    private static final ResourceLocation ANIMATION = new ResourceLocation(MengSamaNetMusic.MOD_ID, "animations/rose_gramophone.animation.json");

    private final ResourceLocation selectedModel;

    public MusicPlayerGeoModel() { this(""); }

    public MusicPlayerGeoModel(String suffix) {
        selectedModel = suffix.isEmpty() ? MODEL : new ResourceLocation(MengSamaNetMusic.MOD_ID,
                MODEL.getPath().replace(".geo.json", suffix + ".geo.json"));
    }

    @Override public ResourceLocation getModelResource(MusicPlayerBlockEntity block) { return selectedModel; }
    @Override public ResourceLocation getTextureResource(MusicPlayerBlockEntity block) { return TEXTURE; }
    @Override public ResourceLocation getAnimationResource(MusicPlayerBlockEntity block) { return ANIMATION; }
}

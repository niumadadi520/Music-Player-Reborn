package com.mengsama.mod.mengsamanetmusic.client.model;

import com.mengsama.mod.mengsamanetmusic.MengSamaNetMusic;
import com.mengsama.mod.mengsamanetmusic.block.PortableMusicPlayerBlockEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public class PortableMusicPlayerGeoModel extends GeoModel<PortableMusicPlayerBlockEntity> {
    private static final ResourceLocation MODEL = new ResourceLocation(MengSamaNetMusic.MOD_ID, "geo/pink_walkman.geo.json");
    private static final ResourceLocation TEXTURE = new ResourceLocation(MengSamaNetMusic.MOD_ID, "textures/block/pink_walkman.png");
    private static final ResourceLocation ANIMATION = new ResourceLocation(MengSamaNetMusic.MOD_ID, "animations/pink_walkman.animation.json");

    private static final ResourceLocation WIRED_MODEL = new ResourceLocation(MengSamaNetMusic.MOD_ID, "geo/pink_wired_display_both.geo.json");
    private static final ResourceLocation WIRED_TEXTURE = new ResourceLocation(MengSamaNetMusic.MOD_ID, "textures/block/pink_wired_display.png");
    private final ResourceLocation selectedModel;
    private boolean wired(PortableMusicPlayerBlockEntity block) { return selectedModel.equals(MODEL) && block!=null && block.hasWiredEarbuds(); }

    public PortableMusicPlayerGeoModel() { this(""); }

    public PortableMusicPlayerGeoModel(String suffix) {
        selectedModel = suffix.isEmpty() ? MODEL : new ResourceLocation(MengSamaNetMusic.MOD_ID,
                MODEL.getPath().replace(".geo.json", suffix + ".geo.json"));
    }

    @Override public ResourceLocation getModelResource(PortableMusicPlayerBlockEntity block) { return wired(block) ? WIRED_MODEL : selectedModel; }
    @Override public ResourceLocation getTextureResource(PortableMusicPlayerBlockEntity block) { return wired(block) ? WIRED_TEXTURE : TEXTURE; }
    @Override public ResourceLocation getAnimationResource(PortableMusicPlayerBlockEntity block) { return ANIMATION; }
}

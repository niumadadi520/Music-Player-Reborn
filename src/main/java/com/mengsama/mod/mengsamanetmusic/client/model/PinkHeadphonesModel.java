package com.mengsama.mod.mengsamanetmusic.client.model;

import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.model.GeoModel;

 
public final class PinkHeadphonesModel<T extends GeoAnimatable> extends GeoModel<T> {
    private final ResourceLocation geometry;
    private final ResourceLocation texture;
    private final ResourceLocation animations;

    public PinkHeadphonesModel(String modId) {
        this.geometry = ResourceLocation.fromNamespaceAndPath(modId, "geo/pink_headphones.geo.json");
        this.texture = ResourceLocation.fromNamespaceAndPath(modId, "textures/armor/pink_headphones.png");
        this.animations = ResourceLocation.fromNamespaceAndPath(modId, "animations/pink_headphones.animation.json");
    }

    @Override
    public ResourceLocation getModelResource(T animatable) {
        return this.geometry;
    }

    @Override
    public ResourceLocation getTextureResource(T animatable) {
        return this.texture;
    }

    @Override
    public ResourceLocation getAnimationResource(T animatable) {
        return this.animations;
    }
}

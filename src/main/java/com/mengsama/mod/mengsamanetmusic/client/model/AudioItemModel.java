package com.mengsama.mod.mengsamanetmusic.client.model;

import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.core.animatable.GeoAnimatable;
import software.bernie.geckolib.model.GeoModel;

 
public final class AudioItemModel<T extends GeoAnimatable> extends GeoModel<T> {
    private final ResourceLocation full, small, texture, animation;
    private boolean compact, wiredDisplay;
    private static final ResourceLocation WIRED_MODEL = new ResourceLocation("mengsamanetmusic", "geo/pink_wired_display_both.geo.json");
    private static final ResourceLocation WIRED_TEXTURE = new ResourceLocation("mengsamanetmusic", "textures/block/pink_wired_display.png");
    public void setWiredDisplay(boolean value) { wiredDisplay=value; }
    public AudioItemModel(String name) {
        full = new ResourceLocation("mengsamanetmusic", "geo/" + name + "_item.geo.json");
        small = name.equals("pink_headphones") ? full : new ResourceLocation("mengsamanetmusic", "geo/" + name + "_item_medium.geo.json");
        texture = new ResourceLocation("mengsamanetmusic", "textures/" + (name.equals("pink_headphones") ? "armor/" : "block/") + name + ".png");
        animation = new ResourceLocation("mengsamanetmusic", "animations/" + name + ".animation.json");
    }
    public void setCompact(boolean compact) { this.compact = compact; }
    @Override public ResourceLocation getModelResource(T item) { return wiredDisplay ? WIRED_MODEL : compact ? small : full; }
    @Override public ResourceLocation getTextureResource(T item) { return wiredDisplay ? WIRED_TEXTURE : texture; }
    @Override public ResourceLocation getAnimationResource(T item) { return animation; }
}

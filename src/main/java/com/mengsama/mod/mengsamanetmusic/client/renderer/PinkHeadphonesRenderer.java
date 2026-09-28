package com.mengsama.mod.mengsamanetmusic.client.renderer;

import com.mengsama.mod.mengsamanetmusic.client.model.PinkHeadphonesModel;
import com.mengsama.mod.mengsamanetmusic.item.PinkHeadphonesItem;
import software.bernie.geckolib.renderer.GeoArmorRenderer;

 
public final class PinkHeadphonesRenderer extends GeoArmorRenderer<PinkHeadphonesItem> {
    public PinkHeadphonesRenderer(String modId) {
        super(new PinkHeadphonesModel<>(modId));
    }
}

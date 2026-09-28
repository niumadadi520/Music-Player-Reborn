package com.mengsama.mod.mengsamanetmusic.client.renderer;

import com.mengsama.mod.mengsamanetmusic.block.PortableMusicPlayerBlockEntity;
import com.mengsama.mod.mengsamanetmusic.client.model.PortableMusicPlayerGeoModel;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import software.bernie.geckolib.renderer.GeoBlockRenderer;

public class PortableMusicPlayerRenderer extends LodMusicBlockRenderer<PortableMusicPlayerBlockEntity> {
    public PortableMusicPlayerRenderer(BlockEntityRendererProvider.Context context) {
        super(new PortableMusicPlayerGeoModel(), new PortableMusicPlayerGeoModel("_medium"), new PortableMusicPlayerGeoModel("_far"));
    }
}

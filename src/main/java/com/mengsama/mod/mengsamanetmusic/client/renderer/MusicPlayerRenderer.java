package com.mengsama.mod.mengsamanetmusic.client.renderer;

import com.mengsama.mod.mengsamanetmusic.block.MusicPlayerBlockEntity;
import com.mengsama.mod.mengsamanetmusic.client.model.MusicPlayerGeoModel;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import software.bernie.geckolib.renderer.GeoBlockRenderer;

public class MusicPlayerRenderer extends LodMusicBlockRenderer<MusicPlayerBlockEntity> {

    public MusicPlayerRenderer(BlockEntityRendererProvider.Context context) {
        super(new MusicPlayerGeoModel(), new MusicPlayerGeoModel("_medium"), new MusicPlayerGeoModel("_far"));
    }

}

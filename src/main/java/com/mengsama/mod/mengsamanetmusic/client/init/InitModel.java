package com.mengsama.mod.mengsamanetmusic.client.init;

import net.neoforged.fml.common.EventBusSubscriber;
import com.mengsama.mod.mengsamanetmusic.MengSamaNetMusic;
import com.mengsama.mod.mengsamanetmusic.client.renderer.MusicPlayerRenderer;
import com.mengsama.mod.mengsamanetmusic.init.ModBlockEntities;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;

@EventBusSubscriber(value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD, modid = MengSamaNetMusic.MOD_ID)
public class InitModel {

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlockEntities.MUSIC_PLAYER.get(), MusicPlayerRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.PORTABLE_MUSIC_PLAYER.get(),
                com.mengsama.mod.mengsamanetmusic.client.renderer.PortableMusicPlayerRenderer::new);
    }
}

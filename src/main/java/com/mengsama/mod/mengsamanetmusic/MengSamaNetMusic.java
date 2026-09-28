package com.mengsama.mod.mengsamanetmusic;

import com.mengsama.mod.mengsamanetmusic.api.NetEaseApi;
import com.mengsama.mod.mengsamanetmusic.api.QqCredentialManager;
import com.mengsama.mod.mengsamanetmusic.config.ConfigManager;
import com.mengsama.mod.mengsamanetmusic.config.ModConfig;
import com.mengsama.mod.mengsamanetmusic.compat.TouhouLittleMaidCompat;
import com.mengsama.mod.mengsamanetmusic.init.*;
import com.mengsama.mod.mengsamanetmusic.network.ModNetwork;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig.Type;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.event.server.ServerStartingEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(MengSamaNetMusic.MOD_ID)
public class MengSamaNetMusic {
    public static final String MOD_ID = "mengsamanetmusic";
    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);
    public static NetEaseApi NET_EASE_API;

    public MengSamaNetMusic() {
        NET_EASE_API = new NetEaseApi();
        QqCredentialManager.init(FMLPaths.CONFIGDIR.get());

        var bus = FMLJavaModLoadingContext.get().getModEventBus();
        ModBlocks.BLOCKS.register(bus);
        ModBlockEntities.BLOCK_ENTITIES.register(bus);
        ModItems.ITEMS.register(bus);
        ModItems.CREATIVE_TABS.register(bus);
        ModSounds.SOUND_EVENTS.register(bus);
        ModMenuTypes.MENU_TYPES.register(bus);

        ModLoadingContext.get().registerConfig(Type.COMMON, ModConfig.init());
        ModNetwork.init();
        com.mengsama.mod.mengsamanetmusic.listening.ListeningNetwork.init();
        com.mengsama.mod.mengsamanetmusic.karaoke.KaraokeNetwork.init();
        TouhouLittleMaidCompat.register();
        com.mengsama.mod.mengsamanetmusic.compat.BackpackAccess.register();

        bus.addListener(this::onCommonSetup);

        MinecraftForge.EVENT_BUS.addListener(this::onServerStarting);
    }

    private void onCommonSetup(FMLCommonSetupEvent event) {
        if (net.minecraftforge.fml.ModList.get().isLoaded("curios"))
            event.enqueueWork(com.mengsama.mod.mengsamanetmusic.compat.CuriosHeadphonesCompat::register);
    }

    private void onServerStarting(ServerStartingEvent event) {

        ConfigManager.initCookies();
         
    }


}

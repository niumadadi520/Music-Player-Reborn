package com.mengsama.mod.mengsamanetmusic;

import com.mengsama.mod.mengsamanetmusic.api.NetEaseApi;
import com.mengsama.mod.mengsamanetmusic.api.QqCredentialManager;
import com.mengsama.mod.mengsamanetmusic.config.ConfigManager;
import com.mengsama.mod.mengsamanetmusic.config.ModConfig;
import com.mengsama.mod.mengsamanetmusic.compat.TouhouLittleMaidCompat;
import com.mengsama.mod.mengsamanetmusic.init.*;
import com.mengsama.mod.mengsamanetmusic.network.ModNetwork;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.fml.ModContainer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig.Type;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(MengSamaNetMusic.MOD_ID)
public class MengSamaNetMusic {
    public static final String MOD_ID = "mengsamanetmusic";
    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);
    public static NetEaseApi NET_EASE_API;

    public MengSamaNetMusic(IEventBus bus, ModContainer container) {
        NET_EASE_API = new NetEaseApi();
        QqCredentialManager.init(FMLPaths.CONFIGDIR.get());


        ModBlocks.BLOCKS.register(bus);
        ModBlockEntities.BLOCK_ENTITIES.register(bus);
        com.mengsama.mod.mengsamanetmusic.item.HeadphonesMaterial.MATERIALS.register(bus);
        ModItems.ITEMS.register(bus);
        ModItems.CREATIVE_TABS.register(bus);
        ModSounds.SOUND_EVENTS.register(bus);
        ModMenuTypes.MENU_TYPES.register(bus);

        container.registerConfig(Type.COMMON, ModConfig.init());
        ModNetwork.init();
        com.mengsama.mod.mengsamanetmusic.listening.ListeningNetwork.init();
        com.mengsama.mod.mengsamanetmusic.karaoke.KaraokeNetwork.init();
        TouhouLittleMaidCompat.register();
        com.mengsama.mod.mengsamanetmusic.compat.BackpackAccess.register();

        bus.addListener(this::onCommonSetup);
        bus.addListener((net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent event) -> {
            event.registerBlockEntity(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK, ModBlockEntities.MUSIC_PLAYER.get(), (device, side) -> device.automationInventory());
            event.registerBlockEntity(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK, ModBlockEntities.PORTABLE_MUSIC_PLAYER.get(), (device, side) -> device.automationInventory());
            event.registerBlockEntity(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK, ModBlockEntities.KARAOKE_DEVICE.get(), (device, side) -> device.automationInventory());
        });
        bus.addListener(com.mengsama.mod.mengsamanetmusic.platform.PayloadChannel::registerAll);

        NeoForge.EVENT_BUS.addListener(this::onServerStarting);
    }

    private void onCommonSetup(FMLCommonSetupEvent event) {
        if (net.neoforged.fml.ModList.get().isLoaded("curios"))
            event.enqueueWork(com.mengsama.mod.mengsamanetmusic.compat.CuriosHeadphonesCompat::register);
    }

    private void onServerStarting(ServerStartingEvent event) {

        ConfigManager.initCookies();
         
    }


}

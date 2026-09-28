package com.mengsama.mod.mengsamanetmusic.init;

import com.mengsama.mod.mengsamanetmusic.MengSamaNetMusic;
import com.mengsama.mod.mengsamanetmusic.gui.MusicPlayerMenu;
import com.mengsama.mod.mengsamanetmusic.gui.MusicPlayerPlaylistMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModMenuTypes {
    public static final DeferredRegister<MenuType<?>> MENU_TYPES = DeferredRegister.create(ForgeRegistries.MENU_TYPES, MengSamaNetMusic.MOD_ID);

    public static final RegistryObject<MenuType<com.mengsama.mod.mengsamanetmusic.earbuds.EarbudMenu>> EARBUDS = MENU_TYPES.register("earbuds", () -> IForgeMenuType.create(com.mengsama.mod.mengsamanetmusic.earbuds.EarbudMenu::new));
    public static final RegistryObject<MenuType<MusicPlayerMenu>> MUSIC_PLAYER = MENU_TYPES.register("music_player",
            () -> MusicPlayerMenu.TYPE);

    public static final RegistryObject<MenuType<MusicPlayerPlaylistMenu>> MUSIC_PLAYER_PLAYLIST = MENU_TYPES.register("music_player_playlist",
            () -> MusicPlayerPlaylistMenu.TYPE);
}

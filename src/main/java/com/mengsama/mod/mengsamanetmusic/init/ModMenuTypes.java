package com.mengsama.mod.mengsamanetmusic.init;

import com.mengsama.mod.mengsamanetmusic.MengSamaNetMusic;
import com.mengsama.mod.mengsamanetmusic.gui.MusicPlayerMenu;
import com.mengsama.mod.mengsamanetmusic.gui.MusicPlayerPlaylistMenu;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.neoforge.registries.DeferredHolder;

public class ModMenuTypes {
    public static final DeferredRegister<MenuType<?>> MENU_TYPES = DeferredRegister.create(BuiltInRegistries.MENU, MengSamaNetMusic.MOD_ID);

    public static final DeferredHolder<MenuType<?>, MenuType<com.mengsama.mod.mengsamanetmusic.earbuds.EarbudMenu>> EARBUDS = MENU_TYPES.register("earbuds", () -> IMenuTypeExtension.create(com.mengsama.mod.mengsamanetmusic.earbuds.EarbudMenu::new));
    public static final DeferredHolder<MenuType<?>, MenuType<MusicPlayerMenu>> MUSIC_PLAYER = MENU_TYPES.register("music_player",
            () -> MusicPlayerMenu.TYPE);

    public static final DeferredHolder<MenuType<?>, MenuType<MusicPlayerPlaylistMenu>> MUSIC_PLAYER_PLAYLIST = MENU_TYPES.register("music_player_playlist",
            () -> MusicPlayerPlaylistMenu.TYPE);
}

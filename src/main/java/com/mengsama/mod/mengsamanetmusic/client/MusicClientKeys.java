package com.mengsama.mod.mengsamanetmusic.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;

 
public final class MusicClientKeys {
    private static final KeyMapping HUD_EDITOR = new KeyMapping("key.mengsamanetmusic.open_hud_editor",
            KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, InputConstants.KEY_H,
            "modmenu.nameTranslation.mengsamanetmusic");
    private MusicClientKeys() {}
    public static void register(RegisterKeyMappingsEvent event) { event.register(HUD_EDITOR); }
    public static void consumeHudEditor(Runnable open) { while (HUD_EDITOR.consumeClick()) open.run(); }
}

package com.mengsama.mod.mengsamanetmusic.compat;

import com.mengsama.mod.mengsamanetmusic.config.ModConfig;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.common.ForgeConfigSpec;

public final class ClothConfigCompat {
    private record Toggle(String title, ForgeConfigSpec.BooleanValue value) {}
    public static void registerModsPage() {
        var factory = new ConfigScreenHandler.ConfigScreenFactory((client, parent) ->
                getConfigBuilder().setParentScreen(parent).build());
        ModLoadingContext.get().registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class, () -> factory);
    }
    public static ConfigBuilder getConfigBuilder() {
        var screen = ConfigBuilder.create().setTitle(Component.literal("音乐机设置"));
        var category = screen.getOrCreateCategory(Component.literal("播放与歌词"));
        for (Toggle option : new Toggle[]{
                new Toggle("女仆歌词", ModConfig.ENABLE_MAID_LYRICS)}) {
            var input = screen.entryBuilder().startBooleanToggle(Component.literal(option.title()), option.value().get());
            category.addEntry(input.setDefaultValue(option.value().getDefault())
                    .setSaveConsumer(option.value()::set).build());
        }
        category.addEntry(screen.entryBuilder().startIntSlider(Component.literal("本次游戏音乐音量"),
                com.mengsama.mod.mengsamanetmusic.client.audio.ClientMusicVolume.percent(), 0, 100)
                .setDefaultValue(100).setSaveConsumer(com.mengsama.mod.mengsamanetmusic.client.audio.ClientMusicVolume::setPercent).build());
        return screen.setSavingRunnable(ModConfig::save);
    }
}

package com.mengsama.mod.mengsamanetmusic.init;

import com.mengsama.mod.mengsamanetmusic.MengSamaNetMusic;
import com.mengsama.mod.mengsamanetmusic.item.MusicListItem;
import com.mengsama.mod.mengsamanetmusic.item.MusicPlayerItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MengSamaNetMusic.MOD_ID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MengSamaNetMusic.MOD_ID);

    public static final RegistryObject<Item> MUSIC_PLAYER = ITEMS.register("music_player",
            () -> com.mengsama.mod.mengsamanetmusic.compat.BackpackAccess.createItem(ModBlocks.PORTABLE_MUSIC_PLAYER.get(), new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> WIRED_EARBUDS = ITEMS.register("pink_wired_earbuds_both", () -> new com.mengsama.mod.mengsamanetmusic.earbuds.EarbudItem(0,"pink_wired_earbuds_both"));
    public static final RegistryObject<Item> BLUETOOTH_LEFT = ITEMS.register("pink_bluetooth_earbuds_left", () -> new com.mengsama.mod.mengsamanetmusic.earbuds.EarbudItem(1,"pink_bluetooth_earbuds_left"));
    public static final RegistryObject<Item> BLUETOOTH_RIGHT = ITEMS.register("pink_bluetooth_earbuds_right", () -> new com.mengsama.mod.mengsamanetmusic.earbuds.EarbudItem(2,"pink_bluetooth_earbuds_right"));
    public static final RegistryObject<Item> BLUETOOTH_CASE = ITEMS.register("pink_bluetooth_case", () -> new com.mengsama.mod.mengsamanetmusic.earbuds.EarbudItem(3,"pink_bluetooth_case"));
    public static final RegistryObject<Item> MUSIC_LIST = ITEMS.register("music_list", MusicListItem::new);
    public static final RegistryObject<Item> PINK_HEADPHONES = ITEMS.register("pink_headphones",
            () -> new com.mengsama.mod.mengsamanetmusic.item.PinkHeadphonesItem(MengSamaNetMusic.MOD_ID,
                    com.mengsama.mod.mengsamanetmusic.item.HeadphonesMaterial.INSTANCE, new Item.Properties().stacksTo(1)));

    public static final RegistryObject<Item> MUSIC_PLAYER_BLOCK = ITEMS.register("music_player_block",
            () -> new com.mengsama.mod.mengsamanetmusic.item.MusicDeviceBlockItem(ModBlocks.MUSIC_PLAYER.get(), new Item.Properties(), "rose_gramophone"));
    public static final RegistryObject<Item> MOD_ICON = ITEMS.register("mod_icon",
            () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> PINK_MICROPHONE = ITEMS.register("pink_microphone",
            () -> new com.mengsama.mod.mengsamanetmusic.karaoke.KaraokeMicrophoneItem(ModBlocks.PINK_MICROPHONE.get(), "pink_microphone"));
    public static final RegistryObject<Item> PINK_HANDHELD_MICROPHONE = ITEMS.register("pink_handheld_microphone",
            () -> new com.mengsama.mod.mengsamanetmusic.karaoke.KaraokeMicrophoneItem(ModBlocks.PINK_HANDHELD_MICROPHONE.get(), "pink_handheld_microphone"));
    public static final RegistryObject<Item> PINK_SPEAKER = ITEMS.register("pink_speaker",
            () -> new com.mengsama.mod.mengsamanetmusic.item.MusicDeviceBlockItem(ModBlocks.PINK_SPEAKER.get(), new Item.Properties().stacksTo(1), "pink_speaker"));
    public static final RegistryObject<Item> PINK_MICROPHONE_STAND = ITEMS.register("pink_microphone_stand",
            () -> new com.mengsama.mod.mengsamanetmusic.item.MicrophoneStandItem(ModBlocks.PINK_MICROPHONE_STAND.get()));
    public static final RegistryObject<CreativeModeTab> TAB = CREATIVE_TABS.register("tab",
            () -> CreativeModeTab.builder().title(Component.translatable("itemGroup.mengsamanetmusic"))
                    .icon(() -> new ItemStack(MOD_ICON.get()))
                    .displayItems((params, output) -> {
                        output.accept(MUSIC_PLAYER.get());
                        output.accept(MUSIC_PLAYER_BLOCK.get());
                        output.accept(PINK_HEADPHONES.get());
                        output.accept(WIRED_EARBUDS.get());
                        output.accept(BLUETOOTH_CASE.get());
                        output.accept(BLUETOOTH_LEFT.get());
                        output.accept(BLUETOOTH_RIGHT.get());
                        output.accept(PINK_MICROPHONE.get());
                        output.accept(PINK_HANDHELD_MICROPHONE.get());
                        output.accept(PINK_SPEAKER.get());
                        output.accept(PINK_MICROPHONE_STAND.get());
                    }).build());
}

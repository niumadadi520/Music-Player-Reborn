package com.mengsama.mod.mengsamanetmusic.init;

import com.mengsama.mod.mengsamanetmusic.MengSamaNetMusic;
import com.mengsama.mod.mengsamanetmusic.block.MusicPlayerBlock;
import com.mengsama.mod.mengsamanetmusic.block.PortableMusicPlayerBlock;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.neoforge.registries.DeferredHolder;

public class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(BuiltInRegistries.BLOCK, MengSamaNetMusic.MOD_ID);

    public static final DeferredHolder<Block, Block> MUSIC_PLAYER = BLOCKS.register("music_player",
            () -> new MusicPlayerBlock());

    public static final DeferredHolder<Block, Block> PORTABLE_MUSIC_PLAYER = BLOCKS.register("portable_music_player",
            () -> new PortableMusicPlayerBlock());

    public static final DeferredHolder<Block, Block> PINK_MICROPHONE = BLOCKS.register("pink_microphone",
            () -> new com.mengsama.mod.mengsamanetmusic.karaoke.KaraokeDeviceBlock("pink_microphone", false));
    public static final DeferredHolder<Block, Block> PINK_HANDHELD_MICROPHONE = BLOCKS.register("pink_handheld_microphone",
            () -> new com.mengsama.mod.mengsamanetmusic.karaoke.KaraokeDeviceBlock("pink_handheld_microphone", false));
    public static final DeferredHolder<Block, Block> PINK_SPEAKER = BLOCKS.register("pink_speaker",
            () -> new com.mengsama.mod.mengsamanetmusic.karaoke.KaraokeDeviceBlock("pink_speaker", true));
    public static final DeferredHolder<Block, Block> PINK_MICROPHONE_STAND = BLOCKS.register("pink_microphone_stand",
            () -> new com.mengsama.mod.mengsamanetmusic.karaoke.KaraokeDeviceBlock("pink_microphone_stand", false));
    public static final DeferredHolder<Block, Block> PINK_MICROPHONE_STAND_TOP = BLOCKS.register("pink_microphone_stand_top",
            com.mengsama.mod.mengsamanetmusic.karaoke.KaraokeStandTopBlock::new);

    }

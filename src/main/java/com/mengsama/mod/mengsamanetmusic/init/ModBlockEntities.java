package com.mengsama.mod.mengsamanetmusic.init;

import com.mengsama.mod.mengsamanetmusic.MengSamaNetMusic;
import com.mengsama.mod.mengsamanetmusic.block.MusicPlayerBlockEntity;
import com.mengsama.mod.mengsamanetmusic.block.PortableMusicPlayerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, MengSamaNetMusic.MOD_ID);

    public static final RegistryObject<BlockEntityType<MusicPlayerBlockEntity>> MUSIC_PLAYER = BLOCK_ENTITIES.register("music_player",
            () -> BlockEntityType.Builder.of(MusicPlayerBlockEntity::new, ModBlocks.MUSIC_PLAYER.get()).build(null));

    public static final RegistryObject<BlockEntityType<PortableMusicPlayerBlockEntity>> PORTABLE_MUSIC_PLAYER = BLOCK_ENTITIES.register("portable_music_player",
            () -> BlockEntityType.Builder.of(PortableMusicPlayerBlockEntity::new, ModBlocks.PORTABLE_MUSIC_PLAYER.get()).build(null));

    public static final RegistryObject<BlockEntityType<com.mengsama.mod.mengsamanetmusic.karaoke.KaraokeBlockEntity>> KARAOKE_DEVICE = BLOCK_ENTITIES.register("karaoke_device",
            () -> BlockEntityType.Builder.of(com.mengsama.mod.mengsamanetmusic.karaoke.KaraokeBlockEntity::new,
                    ModBlocks.PINK_MICROPHONE.get(), ModBlocks.PINK_HANDHELD_MICROPHONE.get(), ModBlocks.PINK_SPEAKER.get(), ModBlocks.PINK_MICROPHONE_STAND.get()).build(null));

    }

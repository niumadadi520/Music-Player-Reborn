package com.mengsama.mod.mengsamanetmusic.mixin;

import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.TickableSoundInstance;
import net.minecraft.client.sounds.ChannelAccess;
import net.minecraft.client.sounds.SoundEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;
import java.util.Map;

@Mixin(SoundEngine.class)
public interface SoundEngineAccessorMixin {
    @Accessor("tickingSounds")
    List<TickableSoundInstance> getTickableSoundInstances();

    @Accessor("instanceToChannel")
    Map<SoundInstance, ChannelAccess.ChannelHandle> getInstanceToChannel();
}

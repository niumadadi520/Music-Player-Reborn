package com.mengsama.mod.mengsamanetmusic.mixin;

import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.client.sounds.SoundManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(SoundManager.class)
public interface SoundManagerAccessorMixin {
    @Accessor("soundEngine")
    SoundEngine getSoundEngine();
}

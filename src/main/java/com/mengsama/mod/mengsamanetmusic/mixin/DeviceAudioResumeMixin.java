package com.mengsama.mod.mengsamanetmusic.mixin;

import com.mengsama.mod.mengsamanetmusic.client.audio.ClientMusicPlayback;
import net.minecraft.client.sounds.SoundManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

 
@Mixin(SoundManager.class)
public abstract class DeviceAudioResumeMixin {
    @Inject(method = "resume", at = @At("RETURN"))
    private void mengsama$restoreDevicePause(CallbackInfo callback) {
        ClientMusicPlayback.reapplyDevicePauses();
    }
}

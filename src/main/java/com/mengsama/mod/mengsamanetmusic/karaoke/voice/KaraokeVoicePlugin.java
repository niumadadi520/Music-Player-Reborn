package com.mengsama.mod.mengsamanetmusic.karaoke.voice;

import com.mengsama.mod.mengsamanetmusic.MengSamaNetMusic;
import de.maxhenkel.voicechat.api.ForgeVoicechatPlugin;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.events.ClientVoicechatConnectionEvent;
import de.maxhenkel.voicechat.api.events.ClientVoicechatInitializationEvent;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.VoicechatServerStartedEvent;
import de.maxhenkel.voicechat.api.events.VoicechatServerStoppedEvent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;

 
@ForgeVoicechatPlugin
public final class KaraokeVoicePlugin implements VoicechatPlugin {
    @Override
    public String getPluginId() { return "mengsamanetmusic_karaoke"; }

    @Override
    public void registerEvents(EventRegistration registration) {
        registration.registerEvent(VoicechatServerStartedEvent.class, event -> {
            try {
                KaraokeVoiceBridge.install(new VoicechatServerRouter(event.getVoicechat()));
            } catch (Exception | LinkageError failure) {
                KaraokeVoiceBridge.install(null);
                MengSamaNetMusic.LOGGER.warn("Simple Voice Chat karaoke integration could not start ({})",
                        failure.getClass().getSimpleName());
            }
        });
        registration.registerEvent(VoicechatServerStoppedEvent.class, event -> KaraokeVoiceBridge.install(null));
        if (FMLEnvironment.dist == Dist.CLIENT) ClientEvents.register(registration);
    }

    private static final class ClientEvents {
        static void register(EventRegistration registration) {
            registration.registerEvent(ClientVoicechatInitializationEvent.class,
                    event -> net.minecraft.client.Minecraft.getInstance().execute(() ->
                            KaraokeVoiceClient.install(new VoicechatClientCapture(event.getVoicechat()))));
            registration.registerEvent(ClientVoicechatConnectionEvent.class, event -> {
                if (!event.isConnected()) net.minecraft.client.Minecraft.getInstance().execute(KaraokeVoiceClient::reset);
            });
        }
    }
}

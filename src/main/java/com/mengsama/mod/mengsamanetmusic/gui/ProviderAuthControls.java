package com.mengsama.mod.mengsamanetmusic.gui;

import com.mengsama.mod.mengsamanetmusic.api.AppleMusicKitAuthorization;
import com.mengsama.mod.mengsamanetmusic.api.QqCredentialManager;
import com.mengsama.mod.mengsamanetmusic.config.ModConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

 
public final class ProviderAuthControls {
    public enum Context { HELD_PLAYER, PORTABLE_BLOCK, JUKEBOX }

    private final Context context;
    private final Screen owner;
    private final Consumer<Component> statusSink;
    private TransparentButton actionButton;
    private int provider;
    private long observedQqRevision = -1;
    private long observedAppleRevision = -1;

    public ProviderAuthControls(Context context, Screen owner, Consumer<Component> statusSink) {
        this.context = context;
        this.owner = owner;
        this.statusSink = statusSink;
    }

    public TransparentButton createButton(int x, int y, int width, int height) {
        actionButton = TransparentButton.builder(Component.empty(), button -> activate())
                .pos(x, y).size(width, height).build();
        refresh(true);
        return actionButton;
    }

    public void setProvider(int provider) {
        this.provider = provider;
        refresh(true);
    }

    public void tick() {
        refresh(false);
    }

    public Context context() {
        return context;
    }

    private void activate() {
        if (provider == 0) { PlayerScreenNavigation.openChild(owner, new NetEaseQrLoginScreen(owner)); return; }
        if (provider == 1) {
            if (QqCredentialManager.hasValidCredential()) {
                QqCredentialManager.clear();
                statusSink.accept(Component.translatable("gui.mengsamanetmusic.qq_login.logged_out"));
                refresh(true);
            } else {
                PlayerScreenNavigation.openChild(owner, new QqLoginScreen(owner));
            }
            return;
        }
        if (provider != 2) return;

        if (AppleMusicKitAuthorization.isBusy()) {
            AppleMusicKitAuthorization.cancel();
            return;
        }
        if (AppleMusicKitAuthorization.isAuthorized()) {
            AppleMusicKitAuthorization.revoke(developerToken());
            return;
        }
        PlayerScreenNavigation.openChild(owner, new AppleMusicAuthScreen(owner));
    }

    private void refresh(boolean force) {
        if (actionButton == null) return;
        long qqRevision = QqCredentialManager.revision();
        long appleRevision = AppleMusicKitAuthorization.revision();
        if (!force && qqRevision == observedQqRevision && appleRevision == observedAppleRevision) return;
        observedQqRevision = qqRevision;
        observedAppleRevision = appleRevision;

        if (provider == 0) {
            actionButton.active = true;
            actionButton.setMessage(Component.literal("扫码登录"));
        } else if (provider == 1) {
            actionButton.active = true;
            actionButton.setMessage(Component.translatable(QqCredentialManager.hasValidCredential()
                    ? "gui.mengsamanetmusic.qq_login.logout" : "gui.mengsamanetmusic.qq_login.button"));
        } else if (provider == 2) {
            boolean configured = AppleMusicKitAuthorization.isDeveloperTokenUsable(developerToken());
            actionButton.active = configured;
            String key = !configured ? "gui.mengsamanetmusic.apple_auth.configure"
                    : AppleMusicKitAuthorization.isBusy() ? "gui.mengsamanetmusic.apple_auth.cancel"
                    : AppleMusicKitAuthorization.isAuthorized() ? "gui.mengsamanetmusic.apple_auth.logout"
                    : "gui.mengsamanetmusic.apple_auth.button";
            actionButton.setMessage(Component.translatable(key));
        }
    }

    public static String developerToken() {
        return ModConfig.APPLE_MUSICKIT_TOKEN == null ? "" : ModConfig.APPLE_MUSICKIT_TOKEN.get().trim();
    }
}

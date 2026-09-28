package com.mengsama.mod.mengsamanetmusic.gui;

import com.mengsama.mod.mengsamanetmusic.api.AppleMusicKitAuthorization;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

 
public final class AppleMusicAuthScreen extends Screen {
    private final Screen parent;
    private Button action;
    private long seenRevision = -1;

    public AppleMusicAuthScreen(Screen parent) {
        super(Component.translatable("gui.mengsamanetmusic.apple_auth.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        action = addRenderableWidget(ThemeButton.builder(Component.empty(), button -> onAction())
                .bounds(width / 2 - 80, height / 2 + 42, 160, 20).build());
        addRenderableWidget(ThemeButton.builder(Component.translatable("gui.mengsamanetmusic.back"), button -> onClose())
                .bounds(width / 2 - 80, height / 2 + 68, 160, 20).build());
        refresh();
        if (AppleMusicKitAuthorization.state() == AppleMusicKitAuthorization.State.IDLE) start();
    }

    private void start() {
        String token = ProviderAuthControls.developerToken();
        if (AppleMusicKitAuthorization.isDeveloperTokenUsable(token)) AppleMusicKitAuthorization.authorize(token);
    }

    private void onAction() {
        switch (AppleMusicKitAuthorization.state()) {
            case STARTING, WAITING -> AppleMusicKitAuthorization.cancel();
            case SUCCESS -> AppleMusicKitAuthorization.revoke(ProviderAuthControls.developerToken());
            default -> start();
        }
    }

    @Override
    public void tick() {
        if (seenRevision != AppleMusicKitAuthorization.revision()) refresh();
    }

    private void refresh() {
        seenRevision = AppleMusicKitAuthorization.revision();
        if (action == null) return;
        AppleMusicKitAuthorization.State state = AppleMusicKitAuthorization.state();
        action.active = state != AppleMusicKitAuthorization.State.STARTING;
        action.setMessage(Component.translatable(switch (state) {
            case STARTING, WAITING -> "gui.mengsamanetmusic.apple_auth.cancel";
            case SUCCESS -> "gui.mengsamanetmusic.apple_auth.logout";
            default -> "gui.mengsamanetmusic.apple_auth.retry";
        }));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        boolean themed=com.mengsama.mod.mengsamanetmusic.gui.theme.ThemeSkin.custom();
        if(themed)com.mengsama.mod.mengsamanetmusic.gui.theme.ThemeSkin.panel(graphics,Math.max(4,width/2-170),height/2-96,Math.min(340,width-8),196,26);
        graphics.drawCenteredString(font, title, width / 2, height / 2 - 82, themed?MusicPlayerSkin.title():0xFFFFFFFF);
        graphics.drawCenteredString(font, Component.translatable("gui.mengsamanetmusic.apple_auth.explanation"),
                width / 2, height / 2 - 56, themed?MusicPlayerSkin.secondary():0xFFD0D0D0);
        graphics.drawCenteredString(font, Component.translatable(stateKey()), width / 2, height / 2 - 8,
                themed ? MusicPlayerSkin.primary() : (AppleMusicKitAuthorization.state() == AppleMusicKitAuthorization.State.SUCCESS ? 0xFF55FF55 : 0xFFFFFFFF));
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private String stateKey() {
        return "gui.mengsamanetmusic.apple_auth.state." + AppleMusicKitAuthorization.state().name().toLowerCase();
    }

    @Override
    public void onClose() {
        if (AppleMusicKitAuthorization.isBusy()) AppleMusicKitAuthorization.cancel();
        if (minecraft != null) PlayerScreenNavigation.returnTo(parent);
    }
}

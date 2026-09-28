package com.mengsama.mod.mengsamanetmusic.compat.backpack;

import com.mengsama.mod.mengsamanetmusic.compat.backpack.charm.BackpackCharmRenderer;
import com.mengsama.mod.mengsamanetmusic.network.BackpackMusicActionPacket;
import com.mengsama.mod.mengsamanetmusic.network.ModNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.p3pp3rf1y.sophisticatedbackpacks.client.gui.BackpackScreen;
import net.p3pp3rf1y.sophisticatedcore.renderdata.RenderInfo;
import java.util.WeakHashMap;

public final class BackpackClient {
    private static final WeakHashMap<BackpackScreen, Button> BUTTONS = new WeakHashMap<>();
    private BackpackClient() {}
    public static java.util.UUID charmDevice(RenderInfo info) {
        return info.getUpgradeItems().stream().filter(s -> s.getItem() instanceof WalkmanUpgradeItem)
                .map(com.mengsama.mod.mengsamanetmusic.item.MusicPlayerItem::getInstanceId)
                .filter(java.util.Objects::nonNull).findFirst().orElse(null);
    }
    public static boolean hasCharm(RenderInfo info) { return info.getUpgradeItems().stream().anyMatch(s -> s.getItem() instanceof WalkmanUpgradeItem); }
    public static void register() {
        MinecraftForge.EVENT_BUS.addListener(BackpackClient::init);
        MinecraftForge.EVENT_BUS.addListener(BackpackClient::render);
        MinecraftForge.EVENT_BUS.addListener(BackpackCharmRenderer::clientTick);
        MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) -> { BUTTONS.clear(); BackpackCharmRenderer.clear(); });
    }
    private static void init(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof BackpackScreen screen)) return;
        Button button = new Button(0, 0, 62, 20, Component.translatable("gui.mengsamanetmusic.open_backpack_music"), b -> {
            int slot = BackpackIntegration.slot(screen.getMenu().getStorageWrapper());
            if (slot >= 0) ModNetwork.CHANNEL.sendToServer(new BackpackMusicActionPacket(screen.getMenu().containerId, slot, false));
        }, narration -> narration.get()) {
            @Override public void renderWidget(GuiGraphics g, int x, int y, float partial) {
                int left = getX(), top = getY();
                g.fill(left, top, left + width, top + height, 0xFF68434B);
                g.fill(left + 1, top + 1, left + width - 1, top + height - 1, isHoveredOrFocused() ? 0xFFC999A3 : 0xFFAD7B86);
                g.fill(left + 2, top + 2, left + width - 2, top + 3, 0xFFF0D9C1);
                g.drawCenteredString(Minecraft.getInstance().font, getMessage(), left + width / 2, top + 6, 0xFFFFEAD5);
            }
        };
        button.setTooltip(Tooltip.create(Component.translatable("gui.mengsamanetmusic.open_backpack_music")));
        BUTTONS.put(screen, button); update(screen, button); event.addListener(button);
    }
    private static void render(ScreenEvent.Render.Pre event) {
        if (event.getScreen() instanceof BackpackScreen screen) {
            Button button = BUTTONS.get(screen); if (button != null) update(screen, button);
        }
    }
    private static void update(BackpackScreen screen, Button button) {
        button.visible = BackpackIntegration.slot(screen.getMenu().getStorageWrapper()) >= 0;
        button.setX(Math.max(2, Math.min(screen.width - button.getWidth() - 2, screen.getGuiLeft() + screen.getXSize() + 4)));
        button.setY(Math.max(2, Math.min(screen.height - button.getHeight() - 2, screen.getGuiTop() + screen.getYSize() - 20)));
    }
}

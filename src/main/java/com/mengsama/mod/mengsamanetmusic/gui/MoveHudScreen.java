package com.mengsama.mod.mengsamanetmusic.gui;

import com.mengsama.mod.mengsamanetmusic.client.BackgroundImporter;
import com.mengsama.mod.mengsamanetmusic.client.MusicPlayerBackground;
import com.mengsama.mod.mengsamanetmusic.config.MusicHudConfig;
import com.mengsama.mod.mengsamanetmusic.config.MusicPlayerUiConfig;
import com.mengsama.mod.mengsamanetmusic.hud.MusicInfoHud;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.CompletableFuture;

 
public class MoveHudScreen extends com.mengsama.mod.mengsamanetmusic.gui.ThemedOverlayScreen {
    private final Screen parent;
    private final MusicHudConfig.Data originalHud;
    private final ThemeColorEditor colorEditor;
    private boolean dragging;
    private boolean syncingControls;
    private double dragOffsetX, dragOffsetY;
    private EditBox hexBox;
    private ChannelSlider red, green, blue, alpha;
    private Button accentTab, backgroundTab;
    private Component validationMessage = Component.empty();
    private MoveHudLayout.Layout layout;

    public MoveHudScreen(Screen parent) {
        super(Component.translatable("gui.mengsamanetmusic.hud_editor.title"));
        this.parent = parent;
        this.originalHud = MusicHudConfig.copy();
        this.colorEditor = new ThemeColorEditor(MusicPlayerUiConfig.get());
    }

    public static void open() {
        Minecraft minecraft = Minecraft.getInstance();
        open(minecraft.screen);
    }

    public static void open(Screen parent) {
         
        if (parent instanceof MusicPlayerScreen player) player.prepareForChildScreen();
        if (parent instanceof MusicPlayerPlaylistScreen player) player.prepareForChildScreen();
        Minecraft.getInstance().setScreen(new MoveHudScreen(parent));
    }

    @Override protected void init() {
        int panelWidth = Math.min(260, Math.max(1, width - MoveHudLayout.MARGIN * 2));
        int contentWidth = Math.max(1, panelWidth - 14);
        int wrappedStatusHeight = font.wordWrapHeight(statusComponent(), contentWidth);
        layout = MoveHudLayout.calculate(width, height, wrappedStatusHeight, font.lineHeight);
        int x = layout.options().x();
        int full = layout.options().width();
        int half = (full - 5) / 2;
        int y = layout.options().y();

        addRenderableWidget(toggle(x, y, half, "gui.mengsamanetmusic.hud_editor.cover", () -> cfg().showCover, v -> cfg().showCover = v));
        addRenderableWidget(toggle(x + half + 5, y, half, "gui.mengsamanetmusic.hud_editor.title_element", () -> cfg().showTitle, v -> cfg().showTitle = v)); y += 17;
        addRenderableWidget(toggle(x, y, half, "gui.mengsamanetmusic.hud_editor.artist", () -> cfg().showArtist, v -> cfg().showArtist = v));
        addRenderableWidget(toggle(x + half + 5, y, half, "gui.mengsamanetmusic.hud_editor.progress", () -> cfg().showProgress, v -> cfg().showProgress = v)); y += 17;
        addRenderableWidget(toggle(x, y, half, "gui.mengsamanetmusic.hud_editor.lyrics", () -> cfg().showLyrics, v -> cfg().showLyrics = v));
        addRenderableWidget(ThemeButton.builder(percent("gui.mengsamanetmusic.hud_editor.scale", cfg().scale), b -> {
            cfg().scale = cfg().scale >= 2F ? 0.5F : cfg().scale + 0.25F;
            b.setMessage(percent("gui.mengsamanetmusic.hud_editor.scale", cfg().scale));
        }).bounds(x + half + 5, y, half, 17).build()); y += 17;
        addRenderableWidget(ThemeButton.builder(percent("gui.mengsamanetmusic.hud_editor.opacity", cfg().opacity), b -> {
            cfg().opacity = cfg().opacity >= 1F ? 0.25F : Math.min(1F, cfg().opacity + 0.15F);
            b.setMessage(percent("gui.mengsamanetmusic.hud_editor.opacity", cfg().opacity));
        }).bounds(x, y, half, 17).build());

        addRenderableWidget(ThemeButton.builder(Component.literal("菜单主题…"), b -> ThemeSelectionScreen.open(this))
                .bounds(x + half + 5, y, half, 17).build());

        List<MoveHudLayout.Rect> targets = layout.colorTargets();
        addRenderableWidget(colorButton(targets.get(0), "gui.mengsamanetmusic.hud_editor.text_color", false, false));
        MoveHudLayout.Rect accentRect = targets.get(1);
        accentTab = addRenderableWidget(ThemeButton.builder(Component.translatable("gui.mengsamanetmusic.hud_editor.accent_tab"),
                b -> switchTarget(ThemeColorEditor.Target.ACCENT)).bounds(accentRect.x(), accentRect.y(), accentRect.width(), accentRect.height()).build());
        addRenderableWidget(colorButton(targets.get(2), "gui.mengsamanetmusic.hud_editor.secondary_text_color", false, true));
        MoveHudLayout.Rect backgroundRect = targets.get(3);
        backgroundTab = addRenderableWidget(ThemeButton.builder(Component.translatable("gui.mengsamanetmusic.hud_editor.background_tab"),
                b -> switchTarget(ThemeColorEditor.Target.BACKGROUND)).bounds(backgroundRect.x(), backgroundRect.y(), backgroundRect.width(), backgroundRect.height()).build());

        MoveHudLayout.Rect rgb = layout.rgbControls();
        x = rgb.x();
        y = rgb.y();
        full = rgb.width();
        half = Math.max(1, (full - 5) / 2);
        red = addRenderableWidget(new ChannelSlider(x, y, half, "R"));
        green = addRenderableWidget(new ChannelSlider(x + half + 5, y, half, "G")); y += 18;
        blue = addRenderableWidget(new ChannelSlider(x, y, half, "B"));
        alpha = addRenderableWidget(new ChannelSlider(x + half + 5, y, half, "Alpha")); y += 20;
        hexBox = new EditBox(font, x + 22, y, full - 88, 18, Component.translatable("gui.mengsamanetmusic.hud_editor.theme_hex"));
        hexBox.setMaxLength(9);
        hexBox.setResponder(this::applyHex);
        addRenderableWidget(hexBox);
        addRenderableWidget(ThemeButton.builder(Component.translatable("gui.mengsamanetmusic.hud_editor.theme_reset"), b -> {
            colorEditor.resetCurrent();
            validationMessage = Component.empty();
            syncColorControls();
        }).bounds(x + full - 62, y, 62, 18).build());
        syncColorControls();

        MoveHudLayout.Rect background = layout.backgroundImport();
        addRenderableWidget(ThemeButton.builder(Component.literal("选择背景图片…"), b -> chooseBackground())
                .bounds(background.x(), background.y(), background.width(), 18).build());
        MoveHudLayout.Rect cancel = layout.cancel();
        MoveHudLayout.Rect save = layout.save();
        addRenderableWidget(ThemeButton.builder(Component.translatable("gui.mengsamanetmusic.hud_editor.cancel"), b -> cancel())
                .bounds(cancel.x(), cancel.y(), cancel.width(), cancel.height()).build());
        addRenderableWidget(ThemeButton.builder(Component.translatable("gui.mengsamanetmusic.hud_editor.save"), b -> save())
                .bounds(save.x(), save.y(), save.width(), save.height()).build());
    }

    private Button colorButton(MoveHudLayout.Rect rect, String key, boolean background, boolean secondary) {
        return ThemeButton.builder(Component.translatable(key), b -> {
            int[] values = background ? new int[]{0xCC101018, 0xCC000000, 0xCC18304A, 0xCC4A1818, 0x00000000}
                    : new int[]{0xFF45302F, 0xFF795D54, 0xFF874654, 0xFF325D53, 0xFF3E516E,
                            0xFFFFFFFF, 0xFFFFE082, 0xFF80DEEA, 0xFFA5D6A7, 0xFFFFAB91, 0xFFB8B8C8};
            if (background) cfg().backgroundColor = next(values, cfg().backgroundColor);
            else if (secondary) cfg().secondaryTextColor = next(values, cfg().secondaryTextColor);
            else cfg().textColor = next(values, cfg().textColor);
        }).bounds(rect.x(), rect.y(), rect.width(), rect.height()).build();
    }

    private Button toggle(int x, int y, int w, String key, BoolGetter getter, BoolSetter setter) {
        return ThemeButton.builder(toggleLabel(key, getter.get()), b -> {
            boolean value = !getter.get(); setter.set(value); b.setMessage(toggleLabel(key, value));
        }).bounds(x, y, w, 17).build();
    }

    @Override public void render(@NotNull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderTransparentBackground(g);
        MoveHudLayout.Rect panel = layout.panel();
        if(com.mengsama.mod.mengsamanetmusic.gui.theme.ThemeSkin.custom())
            com.mengsama.mod.mengsamanetmusic.gui.theme.ThemeSkin.panel(g,panel.x(),panel.y(),panel.width(),panel.height(),17);
        else g.fill(panel.x(), panel.y(), panel.right(), panel.bottom(), 0xE008080C);
        g.drawCenteredString(font, title, panel.x() + panel.width() / 2, 7, com.mengsama.mod.mengsamanetmusic.gui.theme.ThemeSkin.custom()?MusicPlayerSkin.title():0xFFFFFFFF);
        if (panel.x() > 120) g.drawString(font, Component.translatable("gui.mengsamanetmusic.hud_editor.hint"), 8, height - 18, 0xFFFFFFFF);
        if (hexBox != null) {
            int previewX = hexBox.getX() - 21;
            g.fill(previewX, hexBox.getY(), previewX + 18, hexBox.getY() + 18, colorEditor.argb());
            g.renderOutline(previewX, hexBox.getY(), 18, 18, MusicPlayerUiConfig.Values.contrastText(colorEditor.rgb()));
        }
        if (accentTab != null) {
            Button selected = colorEditor.target() == ThemeColorEditor.Target.ACCENT ? accentTab : backgroundTab;
            g.renderOutline(selected.getX() - 1, selected.getY() - 1, selected.getWidth() + 2, selected.getHeight() + 2,
                    MusicPlayerUiConfig.get().accent());
        }
        MoveHudLayout.Rect background = layout.backgroundImport();
        if (background.height() > 18) {
            g.drawString(font, Component.literal("支持 JPG/JPEG/PNG/GIF，导入后自动应用"), background.x(), background.y() + 21, com.mengsama.mod.mengsamanetmusic.gui.theme.ThemeSkin.custom()?MusicPlayerSkin.secondary():0xFFAAAAAA);
        }
        MoveHudLayout.Rect status = layout.status();
        List<net.minecraft.util.FormattedCharSequence> lines = font.split(statusComponent(), status.width());
        int color = com.mengsama.mod.mengsamanetmusic.gui.theme.ThemeSkin.custom() ? MusicPlayerSkin.primary() : (isErrorStatus() ? 0xFFFF6666 : 0xFFAAFFAA);
        int count = Math.min(layout.statusLines(), lines.size());
        for (int i = 0; i < count; i++) g.drawString(font, lines.get(i), status.x(), status.y() + i * font.lineHeight, color);
        MusicInfoHud.renderPreview(g);
        super.render(g, mouseX, mouseY, partialTick);
    }

    private Component statusComponent() {
        String state = MusicPlayerBackground.status();
        if (validationMessage.getString().isEmpty()) return Component.literal(state);
        return Component.literal(validationMessage.getString() + (state.isBlank() ? "" : " · " + state));
    }

    private boolean isErrorStatus() {
        String status = statusComponent().getString();
        return status.contains("失败") || status.contains("无效") || status.contains("错误");
    }

    @Override public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        var bounds = MusicInfoHud.previewBounds(width, height);
        if (button == 0 && bounds.contains(mx, my)) {
            dragging = true; dragOffsetX = mx - bounds.x(); dragOffsetY = my - bounds.y(); return true;
        }
        return false;
    }

    @Override public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (dragging && button == 0) {
            var bounds = MusicInfoHud.previewBounds(width, height);
            if (bounds.width() == 0 || bounds.height() == 0) { dragging = false; return false; }
            cfg().x = Math.max(0, Math.min(width - bounds.width(), (int)(mx - dragOffsetX)));
            cfg().y = Math.max(0, Math.min(height - bounds.height(), (int)(my - dragOffsetY)));
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override public boolean mouseReleased(double mx, double my, int button) {
        if (button == 0 && dragging) { dragging = false; return true; }
        return super.mouseReleased(mx, my, button);
    }

    @Override public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) { cancel(); return true; }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override public void onClose() { cancel(); }

    private void switchTarget(ThemeColorEditor.Target target) {
        colorEditor.switchTo(target);
        validationMessage = Component.empty();
        syncColorControls();
    }

    private void applyHex(String text) {
        if (syncingControls) return;
        if (colorEditor.applyHex(text)) {
            validationMessage = Component.empty();
            syncSlidersOnly();
        } else validationMessage = Component.translatable("gui.mengsamanetmusic.hud_editor.invalid_hex");
    }

    private void applyChannels() {
        if (syncingControls) return;
        colorEditor.setRgb((red.channel() << 16) | (green.channel() << 8) | blue.channel());
        if (colorEditor.target() == ThemeColorEditor.Target.BACKGROUND) colorEditor.setAlpha(alpha.channel());
        validationMessage = Component.empty();
        syncingControls = true;
        hexBox.setValue(colorEditor.hex());
        syncingControls = false;
    }

    private void syncColorControls() {
        syncSlidersOnly();
        syncingControls = true;
        hexBox.setValue(colorEditor.hex());
        syncingControls = false;
        boolean background = colorEditor.target() == ThemeColorEditor.Target.BACKGROUND;
        alpha.visible = background;
        alpha.active = background;
    }

    private void syncSlidersOnly() {
        syncingControls = true;
        int rgb = colorEditor.rgb();
        red.setChannel(rgb >>> 16); green.setChannel(rgb >>> 8); blue.setChannel(rgb);
        alpha.setChannel(colorEditor.alpha());
        syncingControls = false;
    }

    private void chooseBackground() {
        CompletableFuture.supplyAsync(() -> {
            try {
                String selected = org.lwjgl.util.tinyfd.TinyFileDialogs.tinyfd_openFileDialog(
                        "选择播放器背景", "", (org.lwjgl.PointerBuffer) null, "JPG/JPEG/PNG/GIF 图片", false);
                if (selected == null || selected.isBlank()) return "已取消选择";
                java.nio.file.Path applied = BackgroundImporter.importAndConfigure(java.nio.file.Path.of(selected));
                return "已复制 " + applied.getFileName() + "，正在解码…";
            } catch (Exception ex) {
                return "背景应用失败：" + (ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage());
            }
        }, net.minecraft.Util.backgroundExecutor()).thenAccept(message -> Minecraft.getInstance().execute(() -> {
            validationMessage = Component.literal(message);
            if (message.startsWith("已复制")) MusicPlayerBackground.reload();
            rebuildWidgets();
        }));
    }

    private void save() {
        MusicHudConfig.save();
        try { MusicPlayerUiConfig.save(); }
        catch (Exception ex) { validationMessage = Component.translatable("gui.mengsamanetmusic.hud_editor.save_failed"); rebuildWidgets(); return; }
        returnToParent();
    }

    private void cancel() {
        dragging = false;
        MusicHudConfig.restore(originalHud);
        colorEditor.cancel();
        returnToParent();
    }

    private void returnToParent() {
        Minecraft mc = Minecraft.getInstance();
        if (parent instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> container
                && (mc.player == null || mc.player.containerMenu != container.getMenu())) {
             
            mc.setScreen(null);
            return;
        }
        mc.setScreen(parent);
    }

    private MusicHudConfig.Data cfg() { return MusicHudConfig.get(); }
    private static int next(int[] values, int current) {
        for (int i = 0; i < values.length; i++) if (values[i] == current) return values[(i + 1) % values.length];
        return values[0];
    }
    private static Component toggleLabel(String key, boolean value) {
        return Component.translatable(key).append(": ").append(Component.translatable(value ? "options.on" : "options.off"));
    }
    private static Component percent(String key, float value) {
        return Component.translatable(key).append(": " + Math.round(value * 100) + "%");
    }

    private final class ChannelSlider extends AbstractSliderButton {
        private final String label;
        ChannelSlider(int x, int y, int width, String label) { super(x, y, width, 17, Component.empty(), 0); this.label = label; updateMessage(); }
        int channel() { return Math.max(0, Math.min(255, (int) Math.round(value * 255))); }
        void setChannel(int channel) { value = Math.max(0, Math.min(255, channel & 255)) / 255D; updateMessage(); }
        @Override protected void updateMessage() { setMessage(Component.literal(label + ": " + channel())); }
        @Override protected void applyValue() { applyChannels(); }
    }

    @FunctionalInterface private interface BoolGetter { boolean get(); }
    @FunctionalInterface private interface BoolSetter { void set(boolean value); }
}

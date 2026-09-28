package com.mengsama.mod.mengsamanetmusic.karaoke.client;

import com.mengsama.mod.mengsamanetmusic.gui.MusicPlayerSkin;
import com.mengsama.mod.mengsamanetmusic.gui.RosewoodPlayerLayout.Rect;
import com.mengsama.mod.mengsamanetmusic.karaoke.KaraokeNetwork;
import com.mengsama.mod.mengsamanetmusic.karaoke.KaraokeState;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

 
public final class KaraokeSettingsScreen extends com.mengsama.mod.mengsamanetmusic.gui.ThemedOverlayScreen {
    private final AbstractContainerScreen<?> parent;
    private final int containerId;
    private KaraokeState state;
    private KaraokeLayout layout;
    private EditBox code;
    private KaraokeSkin.Button toggle, add, copy, previous, next;
    private final List<KaraokeSkin.Button> remove = new ArrayList<>();
    private VolumeSlider volume;
    private int page;
    private int ticks;
    private String localMessage = "";
    private int localMessageTicks;
    private String hoveredConnection;
    private String serverMessage = "";
    private int serverMessageTicks;

    public KaraokeSettingsScreen(AbstractContainerScreen<?> parent) {
        super(Component.literal("K歌 · 设备设置"));
        this.parent = parent;
        this.containerId = parent.getMenu().containerId;
        this.state = KaraokeUi.current(containerId);
        if (state != null && state.message() != null && !state.message().isBlank()) {
            serverMessage = state.message();
            serverMessageTicks = 120;
        }
    }

    @Override protected void init() {
        layout = new KaraokeLayout(width, height);
        rebuild();
        KaraokeNetwork.sendAction(containerId, KaraokeNetwork.Action.REFRESH, "");
    }

    private void rebuild() {
        String draft = code != null && state != null && state.kind() == KaraokeState.Kind.SPEAKER ? code.getValue() : "";
        clearWidgets();
        code = null;
        toggle = add = copy = previous = next = null;
        volume = null;
        remove.clear();
        addRenderableWidget(KaraokeSkin.button(layout.back(), "返回音乐界面", this::onClose));
        if (state == null) return;
        if (state.kind() == KaraokeState.Kind.SPEAKER) {
            code = edit(layout.input(), "输入麦克风连接码");
            code.setHint(Component.literal("粘贴麦克风连接码"));
            code.setMaxLength(36);
            code.setFilter(value -> value.matches("[0-9a-fA-F-]*"));
            code.setValue(draft);
            add = addRenderableWidget(KaraokeSkin.button(layout.add(), "连接", this::addConnection));
            add.tooltip("输入麦克风的唯一连接码，可连接多个麦克风（最多 32 个）");
            for (int i = 0; i < layout.rows(); i++) {
                final int row = i;
                remove.add(addRenderableWidget(KaraokeSkin.button(layout.remove(i), "移除", () -> removeConnection(row))));
            }
            previous = addRenderableWidget(KaraokeSkin.button(layout.previous(), "‹", () -> { page--; refreshControls(); }));
            previous.tooltip("上一页连接");
            next = addRenderableWidget(KaraokeSkin.button(layout.next(), "›", () -> { page++; refreshControls(); }));
            next.tooltip("下一页连接");
            volume = addRenderableWidget(new VolumeSlider(layout.volume(), state.volume()));
        } else if (KaraokeUi.isMicrophone(state)) {
            code = edit(layout.microphoneCode(), "此麦克风的唯一连接码");
            code.setMaxLength(36);
            code.setEditable(false);
            code.setValue(connectionCode(state.microphoneId()));
            copy = addRenderableWidget(KaraokeSkin.button(layout.copy(), "复制", this::copyCode));
            copy.tooltip("复制唯一连接码，粘贴到音响的 K歌设置中");
            toggle = addRenderableWidget(KaraokeSkin.button(layout.toggle(), "开启麦克风", this::toggleMicrophone));
        } else {
            toggle = addRenderableWidget(KaraokeSkin.button(layout.toggle(), "关闭当前 K歌麦克风",
                    () -> send(KaraokeNetwork.Action.STOP, "")));
            toggle.tooltip("立即停止本人的 K歌采集，不修改 Simple Voice Chat 开关");
        }
        refreshControls();
    }

    private EditBox edit(Rect r, String narration) {
        EditBox field = new EditBox(font, r.x(), r.y(), r.width(), r.height(), Component.literal(narration));
        field.setTextColor(MusicPlayerSkin.primary());
        field.setTextColorUneditable(MusicPlayerSkin.secondary());
        field.setBordered(false);
         
        field.setX(r.x() + 4);
        field.setY(r.y() + 6);
        field.setWidth(Math.max(1, r.width() - 8));
        return addRenderableWidget(field);
    }

    void accept(KaraokeState incoming) {
        if (incoming.containerId() != containerId) return;
        boolean changedKind = state == null || state.kind() != incoming.kind();
        state = incoming;
        if (incoming.message() != null && !incoming.message().isBlank()) {
            serverMessage = incoming.message();
            serverMessageTicks = 120;
        }
        if (changedKind) rebuild();
        else {
            if (volume != null) volume.serverValue(incoming.volume());
            if (code != null && KaraokeUi.isMicrophone(state)) code.setValue(connectionCode(state.microphoneId()));
            refreshControls();
        }
    }

    private void refreshControls() {
        if (state == null) return;
        if (toggle != null) {
            boolean microphone = KaraokeUi.isMicrophone(state);
            toggle.active = microphone ? KaraokeUi.canToggle(state) : state.enabled() && state.mine();
            toggle.lit(state.enabled() && state.mine());
            toggle.setMessage(Component.literal(microphone
                    ? state.enabled() ? state.mine() ? "麦克风已开启 · 点击关闭" : "其他玩家正在使用" : "麦克风已关闭 · 点击开启"
                    : "关闭当前 K歌麦克风"));
            toggle.tooltip(state.enabled() && state.mine() ? "关闭后立即停止此 K歌麦克风的采集"
                    : !state.voiceAvailable() ? "需要客户端和服务器启用 Simple Voice Chat，且麦克风设备可用"
                    : "独立采集麦克风，仅从连接音响播放；不改变普通语音的静音或按键状态");
        }
        if (copy != null) copy.active = state.microphoneId() != null;
        if (add != null) add.active = state.connections().size() < 32 && validCode(code == null ? "" : code.getValue());
        if (previous != null) {
            int last = Math.max(0, (state.connections().size() - 1) / layout.rows());
            page = Math.max(0, Math.min(page, last));
            previous.active = page > 0;
            next.active = page < last;
            for (int i = 0; i < remove.size(); i++) {
                remove.get(i).visible = page * layout.rows() + i < state.connections().size();
            }
        }
    }

    private void toggleMicrophone() {
        if (KaraokeUi.canToggle(state)) send(KaraokeNetwork.Action.TOGGLE, Boolean.toString(!state.enabled()));
    }

    private void addConnection() {
        String value = code == null ? "" : code.getValue().trim();
        if (validCode(value) && state != null && state.connections().size() < 32) {
            send(KaraokeNetwork.Action.ADD_CONNECTION, value);
             
        }
    }

    private void removeConnection(int row) {
        if (state == null) return;
        int index = page * layout.rows() + row;
        if (index >= 0 && index < state.connections().size()) send(KaraokeNetwork.Action.REMOVE_CONNECTION, state.connections().get(index));
    }

    private void copyCode() {
        if (minecraft == null || state == null || state.microphoneId() == null) return;
        minecraft.keyboardHandler.setClipboard(connectionCode(state.microphoneId()));
        localMessage = "连接码已复制，可粘贴到多个音响";
        localMessageTicks = 60;
    }

    private void send(KaraokeNetwork.Action action, String value) {
        if (action != KaraokeNetwork.Action.REFRESH) serverMessageTicks = 0;
        if (validMenu()) KaraokeNetwork.sendAction(containerId, action, value);
    }

    private boolean validMenu() {
        return minecraft != null && minecraft.player != null && minecraft.player.containerMenu == parent.getMenu();
    }

    static String connectionCode(UUID id) { return id == null ? "" : id.toString().replace("-", ""); }
    static boolean validCode(String value) {
        return value != null && (value.matches("[0-9a-fA-F]{32}")
                || value.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"));
    }

    @Override public void tick() {
        if (!validMenu()) { if (minecraft != null) minecraft.setScreen(null); return; }
        if (code != null) {}
        if (localMessageTicks > 0) localMessageTicks--;
        if (serverMessageTicks > 0) serverMessageTicks--;
        if (volume != null) volume.tick();
        refreshControls();
         
        if (++ticks % 100 == 0 && state == null) send(KaraokeNetwork.Action.REFRESH, "");
    }

    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        hoveredConnection = null;
        renderTransparentBackground(g);
        KaraokeSkin.panel(g, layout);
        String title = state == null ? "K歌 · 设备设置" : switch (state.kind()) {
            case HANDHELD -> "K歌 · 手持麦克风";
            case STANDING -> parent.getMenu() instanceof com.mengsama.mod.mengsamanetmusic.gui.MusicPlayerPlaylistMenu menu
                    && menu.getBlockEntity() instanceof com.mengsama.mod.mengsamanetmusic.karaoke.KaraokeBlockEntity device
                    && device.isStand() ? "K歌 · 架上麦克风" : "K歌 · 老式麦克风";
            case SPEAKER -> "K歌 · 音响设置";
            case NONE -> "K歌 · 当前状态";
        };
        text(g, title, 38, 9, layout.panel().width() - 60, MusicPlayerSkin.title());
        if (state == null) {
            text(g, "正在读取设备状态…", 17, 52, layout.panel().width() - 34, MusicPlayerSkin.secondary());
        } else if (state.kind() == KaraokeState.Kind.SPEAKER) renderSpeaker(g, mouseX, mouseY);
        else renderMicrophone(g);
        if (code != null) {
            Rect r = state.kind() == KaraokeState.Kind.SPEAKER ? layout.input() : layout.microphoneCode();
            g.fill(r.x(), r.y(), r.right(), r.bottom(), MusicPlayerSkin.inputBackground());
            g.renderOutline(r.x(), r.y(), r.width(), r.height(), MusicPlayerSkin.edge());
        }
        String message = localMessageTicks > 0 ? localMessage : serverMessageTicks > 0 ? serverMessage : "";
        if (message != null && !message.isBlank()) {
            Rect r = layout.message();
            g.drawString(font, ellipsis(message, r.width()), r.x(), r.y(), MusicPlayerSkin.accent(), false);
        }
        super.render(g, mouseX, mouseY, partialTick);
        if (message != null && !message.isBlank() && layout.message().contains(mouseX, mouseY))
            g.renderTooltip(font, font.split(Component.literal(message), Math.max(80, Math.min(290, width - 24))), mouseX, mouseY);
        else if (hoveredConnection != null) g.renderTooltip(font, Component.literal(hoveredConnection), mouseX, mouseY);
    }

    private void renderSpeaker(GuiGraphics g, int mouseX, int mouseY) {
        if (!layout.compact()) text(g, "多麦克风连接 · 距离不限 · 每台音响独立音量", 17, 34,
                layout.panel().width() - 34, MusicPlayerSkin.secondary());
        if (!layout.compact()) text(g, "已连接 " + state.connections().size() + " / 32", 17, 75,
                layout.panel().width() - 34, MusicPlayerSkin.primary());
        if (state.connections().isEmpty()) {
            Rect r = layout.row(0);
            g.drawString(font, ellipsis("粘贴麦克风连接码后点击“连接”", r.width()), r.x(), r.y() + 3, MusicPlayerSkin.secondary(), false);
        }
        for (int i = 0; i < layout.rows(); i++) {
            int index = page * layout.rows() + i;
            if (index >= state.connections().size()) break;
            Rect r = layout.row(i);
            String value = state.connections().get(index);
            g.fill(r.x(), r.y(), r.right(), r.bottom(), r.contains(mouseX, mouseY) ? MusicPlayerSkin.listHover() : MusicPlayerSkin.listBackground());
            g.drawString(font, ellipsis((index + 1) + ". " + value.replace("-", ""), r.width() - 45),
                    r.x() + 3, r.y() + 4, MusicPlayerSkin.primary(), false);
            if (r.contains(mouseX, mouseY) && !layout.remove(i).contains(mouseX, mouseY)) hoveredConnection = value;
        }
        String pages = (page + 1) + " / " + Math.max(1, (state.connections().size() + layout.rows() - 1) / layout.rows());
        text(g, "连接列表 " + pages, 62, layout.paginationY() + 4, layout.panel().width() - 124, MusicPlayerSkin.secondary());
    }

    private void renderMicrophone(GuiGraphics g) {
        boolean microphone = KaraokeUi.isMicrophone(state);
        String status = state.enabled() ? state.mine() ? "● K歌麦克风已开启，正在独立采集" : "● 此麦克风正在被其他玩家使用" : "○ K歌麦克风已关闭";
        text(g, status, 17, 34, layout.panel().width() - 34,
                state.enabled() ? MusicPlayerSkin.accent() : MusicPlayerSkin.secondary());
        if (microphone) {
            if (!layout.compact()) text(g, "唯一连接码 · 复制到音响即可连接", 17, 60,
                    layout.panel().width() - 34, MusicPlayerSkin.primary());
            int y = layout.compact() ? 111 : 146;
            text(g, "独立采集，仅从连接的音响播放", 17, y, layout.panel().width() - 34, MusicPlayerSkin.secondary());
            if (!layout.compact()) {
                text(g, "普通语音静音或未按说话键时，开启此开关仍会采集", 17, y + 12,
                        layout.panel().width() - 34, MusicPlayerSkin.secondary());
                if (!state.voiceAvailable()) text(g, "语音联动不可用，请检查 Simple Voice Chat 和麦克风", 17, y + 26,
                        layout.panel().width() - 34, MusicPlayerSkin.accent());
            }
        } else {
            text(g, "手持麦克风，或对立式麦克风 Shift + 右键", 17, layout.compact() ? 50 : 60,
                    layout.panel().width() - 34, MusicPlayerSkin.primary());
            if (!layout.compact()) text(g, "打开音响的音乐界面后，点“音响设置”添加连接", 17, 77,
                    layout.panel().width() - 34, MusicPlayerSkin.secondary());
            if (state.enabled() && state.mine()) text(g, "当前连接码：" + connectionCode(state.microphoneId()), 17,
                    layout.compact() ? 113 : 147, layout.panel().width() - 34, MusicPlayerSkin.secondary());
        }
    }

    private void text(GuiGraphics g, String value, int x, int y, int available, int color) {
        g.drawString(font, ellipsis(value, available), layout.panel().x() + x, layout.panel().y() + y, color, false);
    }

    private String ellipsis(String text, int available) {
        return font.width(text) <= available ? text : font.plainSubstrByWidth(text, Math.max(1, available - font.width("…"))) + "…";
    }

    @Override public boolean keyPressed(int key, int scanCode, int modifiers) {
        if ((key == 257 || key == 335) && code != null && code.isFocused()
                && state != null && state.kind() == KaraokeState.Kind.SPEAKER) { addConnection(); return true; }
        if (minecraft != null && minecraft.options.keyInventory.isActiveAndMatches(InputConstants.getKey(key, scanCode))
                && (code == null || !code.isFocused())) { onClose(); return true; }
        return super.keyPressed(key, scanCode, modifiers);
    }

    @Override public void onClose() {
        if (volume != null) volume.flush();
        if (minecraft != null) minecraft.setScreen(validMenu() ? parent : null);
    }

    @Override public void resize(Minecraft minecraft, int width, int height) {
        if (volume != null) volume.flush();
        super.resize(minecraft, width, height);
    }

    @Override public boolean isPauseScreen() { return false; }

    private final class VolumeSlider extends AbstractSliderButton {
        private boolean dirty;
        private int quietTicks;
        VolumeSlider(Rect r, int percent) {
            super(r.x(), r.y(), r.width(), r.height(), Component.empty(), Math.max(0, Math.min(100, percent)) / 100D);
            updateMessage();
            setTooltip(Tooltip.create(Component.literal("此音响的人声及播放音乐的音量，0% 为静音；拖动、方向键或滚轮调整")));
        }
        int percent() { return (int) Math.round(value * 100); }
        @Override protected void updateMessage() { setMessage(Component.literal("音响音量 " + percent() + "%")); }
        @Override protected void applyValue() { dirty = true; quietTicks = 0; updateMessage(); }
        void serverValue(int percent) {
            if (!dirty) { value = Math.max(0, Math.min(100, percent)) / 100D; updateMessage(); }
        }
        void tick() { if (dirty && ++quietTicks >= 6) flush(); }
        void flush() {
            if (dirty) { dirty = false; send(KaraokeNetwork.Action.SET_VOLUME, Integer.toString(percent())); }
        }
        @Override public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double delta) {
            if (!active || !visible || !isMouseOver(mouseX, mouseY) || delta == 0) return false;
            value = Math.max(0, Math.min(1, value + (delta > 0 ? .01 : -.01)));
            applyValue();
            return true;
        }
        @Override public void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            g.drawCenteredString(font, getMessage(), getX() + width / 2, getY(), MusicPlayerSkin.primary());
            MusicPlayerSkin.renderProgress(g, new Rect(getX() + 5, getY() + 14, Math.max(1, width - 10), 4), (float) value);
            if (isHoveredOrFocused()) g.renderOutline(getX(), getY(), width, height, MusicPlayerSkin.accent());
        }
    }
}

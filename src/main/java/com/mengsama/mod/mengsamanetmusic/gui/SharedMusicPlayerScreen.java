package com.mengsama.mod.mengsamanetmusic.gui;

import com.mengsama.mod.mengsamanetmusic.api.SearchGeneration;
import com.mengsama.mod.mengsamanetmusic.api.SongInfo;
import com.mengsama.mod.mengsamanetmusic.config.MusicPlayerUiConfig;
import com.mengsama.mod.mengsamanetmusic.client.MusicPlayerBackground;
import com.mengsama.mod.mengsamanetmusic.client.SongCoverCache;
import com.mengsama.mod.mengsamanetmusic.client.lyric.ClientLyricStore;
import com.mengsama.mod.mengsamanetmusic.client.lyric.PlaybackSeekUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.jetbrains.annotations.NotNull;
import net.minecraft.world.inventory.AbstractContainerMenu;

 


abstract class SharedMusicPlayerScreen<M extends AbstractContainerMenu> extends AbstractContainerScreen<M> {

    protected SharedMusicPlayerScreen(M menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    protected static final int TAB_SEARCH = 0;

    protected static final int TAB_PLAYLIST = 1;

    protected static final int TAB_FAVORITES = 2;

    protected static final int TAB_LYRICS = 3;

    protected static final int TAB_SETTINGS = 4;

    protected int currentTab = TAB_SEARCH;







    protected EditBox searchBox;

    protected TransparentButton tabSearchBtn;

    protected TransparentButton tabPlaylistBtn;

    protected TransparentButton tabLyricsBtn;

    protected LyricList lyricList;

    protected FavoriteSongList favoriteList;

    protected TransparentButton tabFavoritesBtn;

    protected TransparentButton tabSettingsBtn;

    protected boolean draggingProgress;

    protected int previewSeekSecond;

     


    protected final SearchGeneration playbackRequests = new SearchGeneration();

    protected RosewoodPlayerLayout skinLayout;

    private TransparentButton pagePrevious, pageNext, pageSummary, pageJump;
    private EditBox pageInput;
    private TransparentButton accountPlaylistButton, listeningRankingButton;

    protected final void initFavoriteTools(java.util.function.BiConsumer<SongInfo, Boolean> activate) {
        accountPlaylistButton = pageButton(skinLayout.control(RosewoodPlayerLayout.Control.SOURCE).at(leftPos, topPos), "歌单", () ->
                PlayerScreenNavigation.openChild(this, new AccountPlaylistScreen(this, activate)));
        listeningRankingButton = pageButton(skinLayout.control(RosewoodPlayerLayout.Control.AUTH).at(leftPos, topPos), "听歌排行榜", () ->
                PlayerScreenNavigation.openChild(this, new ListeningRankingScreen(this)));
        updateFavoriteTools();
    }

    private void updateFavoriteTools() {
        if (accountPlaylistButton == null) return;
        accountPlaylistButton.visible = listeningRankingButton.visible = currentTab == TAB_FAVORITES;
        accountPlaylistButton.active = listeningRankingButton.active = currentTab == TAB_FAVORITES;
    }

    protected final void initPagination() {
        var bar = SongPageLayout.within(skinLayout.content().at(leftPos, topPos));
        pagePrevious = pageButton(bar.previous(), "上一页", () -> changePage(-1));
        pageNext = pageButton(bar.next(), "下一页", () -> changePage(1));
        pageSummary = pageButton(bar.summary(), "1 / 1 页", () -> {});
        pageSummary.active = false;
        pageJump = pageButton(bar.jump(), "跳转", this::jumpToPage);
        var input = bar.input();
        pageInput = new EditBox(font, input.x() + 3, input.y() + Math.max(0, (input.height() - 9) / 2),
                Math.max(1, input.width() - 6), 12, Component.literal("指定页码"));
        pageInput.setBordered(false);
        pageInput.setMaxLength(6);
        pageInput.setFilter(value -> value.matches("[0-9]*"));
        pageInput.setHint(Component.literal("页码"));
        pageInput.setTextColor(MusicPlayerSkin.primary());
        pageInput.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal("输入页码后点击跳转或按 Enter")));
        addRenderableWidget(pageInput);
        updatePagination();
    }

    private TransparentButton pageButton(RosewoodPlayerLayout.Rect rect, String label, Runnable action) {
        return addRenderableWidget(TransparentButton.builder(Component.literal(label), b -> action.run())
                .pos(rect.x(), rect.y()).size(rect.width(), rect.height()).build());
    }

    private PagedSongList<?> pagedList() {
        return activeList() instanceof PagedSongList<?> list ? list : null;
    }

    private void changePage(int delta) {
        var list = pagedList();
        if (list != null) list.goToPage(list.page() + delta);
        updatePagination();
    }

    private void jumpToPage() {
        var list = pagedList();
        if (list == null || pageInput == null || pageInput.getValue().isBlank()) return;
        try { list.goToPage(Integer.parseInt(pageInput.getValue())); }
        catch (NumberFormatException invalid) { return; }
        pageInput.setValue(Integer.toString(list.page()));
        updatePagination();
    }

    protected final boolean isPageInputFocused() { return pageInput != null && pageInput.visible && pageInput.isFocused(); }

    protected final boolean handlePageKey(int key) {
        if (isPageInputFocused() && (key == 257 || key == 335)) { jumpToPage(); return true; }
        return false;
    }

    private void updatePagination() {
        updateFavoriteTools();
        if (pageInput == null) return;
        var list = pagedList();
        boolean visible = list != null;
        pagePrevious.visible = pageNext.visible = pageSummary.visible = pageJump.visible = pageInput.visible = visible;
        if (!visible) {
            pageInput.setFocused(false);
            if (getFocused() == pageInput) setFocused(null);
            return;
        }
        pagePrevious.active = list.page() > 1;
        pageNext.active = list.page() < list.pageCount();
        pageJump.active = list.songCount() > 0;
        pageSummary.setMessage(Component.literal(list.page() + " / " + list.pageCount() + " 页"));
    }

    @Override protected void containerTick() {
        super.containerTick();
        if (pageInput != null && pageInput.visible) {}
    }

    protected TransparentButton addSkinButton(RosewoodPlayerLayout.Control control, String label, TransparentButton.OnPress action) {
        var r = skinLayout.control(control).at(leftPos, topPos);
        var button = TransparentButton.builder(Component.literal(label), action).pos(r.x(), r.y()).size(r.width(), r.height()).build();
        button.setSkinControl(control);
        button.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(label)));
        return this.addRenderableWidget(button);
    }

    protected void switchTab(int tab) {
        this.currentTab = tab;
        updateTabVisibility();
        if (this.tabSearchBtn != null)
            this.tabSearchBtn.setSelected(tab == TAB_SEARCH);
        if (this.tabPlaylistBtn != null)
            this.tabPlaylistBtn.setSelected(tab == TAB_PLAYLIST);
        if (this.tabLyricsBtn != null)
            this.tabLyricsBtn.setSelected(tab == TAB_LYRICS);
        if (this.tabFavoritesBtn != null)
            this.tabFavoritesBtn.setSelected(tab == TAB_FAVORITES);
        if (this.tabSettingsBtn != null)
            this.tabSettingsBtn.setSelected(tab == TAB_SETTINGS);
        if (this.favoriteList != null && tab == TAB_FAVORITES) {
            this.favoriteList.applyFilter(this.searchBox == null ? "" : this.searchBox.getValue());
            this.favoriteList.refresh();
        }
        if (tab == TAB_PLAYLIST) {
            refreshPlaylist();
        }
        if (this.lyricList != null && tab == TAB_LYRICS) {
            this.lyricList.refresh();
        }
        updatePagination();
    }

    protected String formatTime(int seconds) {
        return String.format("%d:%02d", seconds / 60, seconds % 60);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTicks, int mouseX, int mouseY) {
        updatePagination();
        renderTransparentBackground(graphics);
        var ui = MusicPlayerUiConfig.get();
        MusicPlayerBackground.renderCover(graphics, 0, 0, this.width, this.height, ui.background());
        MusicPlayerSkin.renderPanel(graphics, leftPos, topPos, skinLayout);
        if (pageInput != null && pageInput.visible) {
            var input = SongPageLayout.within(skinLayout.content().at(leftPos, topPos)).input();
            graphics.fill(input.x(), input.y(), input.right(), input.bottom(), MusicPlayerSkin.inputBackground());
            graphics.renderOutline(input.x(), input.y(), input.width(), input.height(),
                    pageInput.isFocused() ? MusicPlayerSkin.accent() : MusicPlayerSkin.edge());
        }
        if (this.searchBox != null && this.searchBox.visible && this.searchBox.isFocused()) {
            var input = skinLayout.searchField().at(leftPos, topPos);
            graphics.renderOutline(input.x(), input.y(), input.width(), input.height(), MusicPlayerSkin.accent());
        }
    }

    protected void renderProgressBar(GuiGraphics graphics, int x, int y) {
        var track = skinLayout.track().at(x, y);
        SongInfo song = getPlayingSongInfo();
        int tick = getPlayingTick();
        int second = draggingProgress ? previewSeekSecond : Math.max(0, PlaybackSeekUtil.secondAtTick(tick));
        float fraction = song != null && song.songTime > 0 ? Math.min(1F, (float) second / song.songTime) : 0F;
        MusicPlayerSkin.renderProgress(graphics, track, fraction);
        if (song != null && song.songTime > 0) {
            graphics.drawString(font, formatTime(second), track.x(), track.bottom() + 3, MusicPlayerSkin.secondary(), false);
            String total = formatTime(song.songTime);
            graphics.drawString(font, total, track.right() - font.width(total), track.bottom() + 3, MusicPlayerSkin.secondary(), false);
        }
    }

    protected void renderNowPlaying(GuiGraphics graphics, int x, int y) {
        SongInfo song = getPlayingSongInfo();
        var cover = skinLayout.control(RosewoodPlayerLayout.Control.COVER).at(x, y);
        if (song != null) {
            var texture = SongCoverCache.getOrRequest(song.preferredCoverUrl());
            if (texture != null && cover.width() > 4 && cover.height() > 4) {
                int size = Math.min(cover.width(), cover.height()) - 4;
                graphics.blit(texture, cover.x() + 2, cover.y() + 2, 0, 0, size, size, size, size);
            }
        }
        int textX = cover.right() + 7;
        int maxWidth = Math.max(1, x + imageWidth - 18 - textX);
        String title = song == null ? "等待播放音乐" : song.songName == null ? "未知歌曲" : song.songName;
        graphics.drawString(font, font.plainSubstrByWidth(title, maxWidth), textX, cover.y() + 2, MusicPlayerSkin.primary(), false);
        String detail = song == null ? "搜索歌曲或打开设备歌单" : (isPlaybackPaused() ? "已暂停 · " : isPlaying() ? "正在播放 · " : "已停止 · ") + SongInfo.getSourceDisplayName(song.source);
        if (cover.height() >= 24) {
            graphics.drawString(font, font.plainSubstrByWidth(detail, maxWidth), textX, cover.y() + 13, MusicPlayerSkin.secondary(), false);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (pageInput != null && pageInput.visible && pageInput.mouseClicked(mouseX, mouseY, button)) {
            if (searchBox != null) searchBox.setFocused(false);
            setFocused(pageInput);
            return true;
        }
        if (button == 0 && updateSeekPreview(mouseX, mouseY)) {
            playbackRequests.invalidate();
            draggingProgress = true;
            return true;
        }
        if (this.searchBox != null && this.searchBox.mouseClicked(mouseX, mouseY, button)) {
            this.setFocused(this.searchBox);
            return true;
        }
         
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == 0 && draggingProgress) {
            updateSeekPreview(mouseX, mouseY);
            return true;
        }
         
         
        var focused = this.getFocused();
        if (button == 0 && this.isDragging() && focused != null && focused == activeList() && focused.mouseDragged(mouseX, mouseY, button, dragX, dragY))
            return true;
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    protected boolean updateSeekPreview(double mouseX, double mouseY) {
        SongInfo song = getPlayingSongInfo();
        var track = skinLayout.track().at(leftPos, topPos);
        if (song == null || song.songTime <= 0)
            return false;
        if (!draggingProgress && (mouseX < track.x() || mouseX >= track.right() || mouseY < track.y() - 4 || mouseY >= track.bottom() + 5))
            return false;
        previewSeekSecond = PlaybackSeekUtil.secondAtFraction((mouseX - track.x()) / track.width(), song.songTime);
        return true;
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double delta) {
         
        return super.mouseScrolled(mouseX, mouseY, horizontal, delta);
    }

    protected abstract void updateTabVisibility();

    protected abstract void refreshPlaylist();

    protected abstract SongInfo getPlayingSongInfo();

    protected abstract int getPlayingTick();

    protected abstract boolean isPlaybackPaused();

    protected abstract boolean isPlaying();

    protected abstract String getPlaybackTarget();

    protected abstract net.minecraft.client.gui.components.events.GuiEventListener activeList();

    protected class LyricList extends ThemedSelectionList<LyricList.Entry> {

        private String renderedIdentity = "";

        private long renderedGeneration = Long.MIN_VALUE;

        private ClientLyricStore.State renderedState;

        private int currentLine = Integer.MIN_VALUE;

        private boolean autoFollow = true;

        public LyricList(Minecraft mc, int w, int h, int top, int bottom, int itemH) {
            super(mc, w, h, top, bottom, itemH);
            
            
        }

        @Override
        public int getRowWidth() {
            return this.getWidth() - 16;
        }

        @Override
        protected int getScrollbarPosition() {
            return this.getX() + this.getWidth() - 6;
        }

        public void refresh() {
            String target = getPlaybackTarget();
            SongInfo song = getPlayingSongInfo();
            ClientLyricStore.Snapshot snapshot = ClientLyricStore.bind(target, song);
            int tick = getPlayingTick();
            int line = ClientLyricStore.lineIndexAtTick(snapshot.data(), tick);
            boolean identityChanged = !snapshot.identity().equals(renderedIdentity) || snapshot.generation() != renderedGeneration;
            boolean stateChanged = snapshot.state() != renderedState;
            if (!identityChanged && !stateChanged && line == currentLine)
                return;
            double oldScroll = getScrollAmount();
            renderedIdentity = snapshot.identity();
            renderedGeneration = snapshot.generation();
            renderedState = snapshot.state();
            currentLine = line;
            this.clearEntries();
            if (snapshot.state() == ClientLyricStore.State.LOADING) {
                this.addEntry(new Entry("歌词加载中…", false));
            } else if (snapshot.state() == ClientLyricStore.State.FAILED) {
                this.addEntry(new Entry("歌词加载失败", false));
            } else if (snapshot.data().lines().isEmpty()) {
                String empty = song != null && "apple".equals(song.source) ? "Apple Music 暂无歌词" : "暂无歌词";
                this.addEntry(new Entry(empty, false));
            } else {
                int index = 0;
                for (String text : snapshot.data().lines().values()) this.addEntry(new Entry(text, index++ == currentLine));
            }
            if (identityChanged) {
                autoFollow = true;
                if (currentLine >= 0 && currentLine < getItemCount())
                    centerScrollOn(getEntry(currentLine));
                else
                    setScrollAmount(0);
            } else if (autoFollow && currentLine >= 0 && currentLine < getItemCount()) {
                centerScrollOn(getEntry(currentLine));
            } else {
                setScrollAmount(oldScroll);
            }
        }

        @Override
        public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double delta) {
            boolean handled = super.mouseScrolled(mouseX, mouseY, horizontal, delta);
            if (handled)
                autoFollow = false;
            return handled;
        }

        private class Entry extends ObjectSelectionList.Entry<Entry> {

            private final String text;

            private final boolean current;

            Entry(String text, boolean current) {
                this.text = text == null ? "" : text;
                this.current = current;
            }

            @Override
            public void render(GuiGraphics g, int index, int y, int x, int ew, int eh, int mx, int my, boolean hovered, float pt) {
                if (current) {
                    g.fill(x, y - 1, x + ew, y + eh, MusicPlayerSkin.selection());
                    g.fill(x, y - 1, x + 2, y + eh, MusicPlayerSkin.accent());
                }
                String original = text, translation = "";
                int split = text.indexOf('\n');
                if (split >= 0) {
                    original = text.substring(0, split);
                    translation = text.substring(split + 1);
                }
                int color = current ? MusicPlayerSkin.accent() : (hovered ? MusicPlayerSkin.primary() : MusicPlayerSkin.secondary());
                drawLyric(g, original, x + 8, y + 2, ew - 12, color);
                if (!translation.isBlank())
                    drawLyric(g, translation, x + 8, y + 12, ew - 12, current ? MusicPlayerSkin.primary() : MusicPlayerSkin.secondary());
            }

            private void drawLyric(GuiGraphics g, String value, int x, int y, int width, int color) {
                String display = value;
                if (font.width(display) > width)
                    display = font.plainSubstrByWidth(display, Math.max(1, width - font.width("…"))) + "…";
                g.drawString(font, display, x, y, color, false);
            }

            @Override
            @NotNull
            public Component getNarration() {
                return Component.literal(text);
            }
        }
    }
}

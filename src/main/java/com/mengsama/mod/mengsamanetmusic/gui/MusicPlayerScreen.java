package com.mengsama.mod.mengsamanetmusic.gui;

import com.mengsama.mod.mengsamanetmusic.MengSamaNetMusic;
import com.mengsama.mod.mengsamanetmusic.api.AppleMusicApi;
import com.mengsama.mod.mengsamanetmusic.api.NetEaseApi;
import com.mengsama.mod.mengsamanetmusic.api.NetEaseSearchResult;
import com.mengsama.mod.mengsamanetmusic.api.NetEaseSearchMetadataLoader;
import com.mengsama.mod.mengsamanetmusic.api.SearchGeneration;
import com.mengsama.mod.mengsamanetmusic.api.QqMusicUtils;
import com.mengsama.mod.mengsamanetmusic.api.SongInfo;
import com.mengsama.mod.mengsamanetmusic.api.VipCookieState;
import com.mengsama.mod.mengsamanetmusic.config.MusicPlayerUiConfig;
import com.mengsama.mod.mengsamanetmusic.client.MusicPlayerBackground;
import com.mengsama.mod.mengsamanetmusic.client.ClientFavoriteStore;
import com.mengsama.mod.mengsamanetmusic.client.audio.ClientMusicPlayback;
import com.mengsama.mod.mengsamanetmusic.client.lyric.ClientLyricStore;
import com.mengsama.mod.mengsamanetmusic.item.MusicListItem;
import com.mengsama.mod.mengsamanetmusic.item.MusicPlayerItem;
import com.mengsama.mod.mengsamanetmusic.network.ModNetwork;
import com.mengsama.mod.mengsamanetmusic.network.PlayerAddSongPacket;
import com.mengsama.mod.mengsamanetmusic.network.PlayerRemoveSongPacket;
import com.mengsama.mod.mengsamanetmusic.network.ReturnToMaidGuiPacket;
import com.mengsama.mod.mengsamanetmusic.network.SeekPlaybackPacket;
import com.mengsama.mod.mengsamanetmusic.util.PlayMode;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jetbrains.annotations.NotNull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@OnlyIn(Dist.CLIENT)
public class MusicPlayerScreen extends SharedMusicPlayerScreen<MusicPlayerMenu> {

    private TransparentButton sourceButton;

    private TransparentButton searchButton;

    private TransparentButton qqLoginButton;

    private final ProviderAuthControls providerAuth = new ProviderAuthControls(ProviderAuthControls.Context.HELD_PLAYER, this, message -> this.statusMessage = message);

     


    private int searchSource = 0;

    private TransparentButton playButton;

    private TransparentButton stopButton;

    private TransparentButton nextButton;

    private TransparentButton prevButton;

    private TransparentButton modeButton;

    private TransparentButton broadcastButton;

    private TransparentButton settingsEarbudButton;

    private TransparentButton backToMaidButton;

    private boolean returningToMaid;

    private SearchResultList resultList;

    private PlaylistList playlistList;

    private TransparentButton settingsThemeButton;

    private boolean openingSettings;

    private boolean isSearching;

    private final SearchGeneration searchGeneration = new SearchGeneration();

    private Component statusMessage = Component.empty();

    private int lyricRefreshCounter = 0;

    public MusicPlayerScreen(MusicPlayerMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        MusicPlayerUiConfig.Values ui = MusicPlayerUiConfig.get();
        this.imageWidth = ui.screenWidth;
        this.imageHeight = ui.screenHeight;
    }

    @Override
    protected void init() {
        MusicPlayerUiConfig.Values ui = MusicPlayerUiConfig.get();
        this.skinLayout = RosewoodPlayerLayout.fit(ui.screenWidth, ui.screenHeight, this.width, this.height);
        this.imageWidth = skinLayout.width();
        this.imageHeight = skinLayout.height();
        this.openingSettings = false;
        int initialTab = this.currentTab;
        super.init();
        int cx = this.leftPos, cy = this.topPos;
        addSkinButton(RosewoodPlayerLayout.Control.CLOSE, "关闭播放器", b -> this.onClose());
        if (this.menu.getBoundEntityId() != null) {
            var titleRect = skinLayout.title().at(cx, cy);
            int backWidth = Math.min(96, Math.max(60, titleRect.width() / 2));
            this.backToMaidButton = this.addRenderableWidget(TransparentButton.builder(Component.translatable("gui.mengsamanetmusic.music_player.back_to_maid"), b -> returnToMaid()).pos(titleRect.right() - backWidth, cy + 5).size(backWidth, 18).build());
        }
        if (this.menu.getBackpackBinding() != null) {
            var titleRect = skinLayout.title().at(cx, cy);
            int backWidth = Math.min(96, Math.max(60, titleRect.width() / 2));
            this.addRenderableWidget(TransparentButton.builder(Component.translatable("gui.mengsamanetmusic.back_to_backpack"), b -> ModNetwork.CHANNEL.sendToServer(new com.mengsama.mod.mengsamanetmusic.network.BackpackMusicActionPacket(menu.containerId, -1, true))).pos(titleRect.right() - backWidth, cy + 5).size(backWidth, 18).build());
        }
        if (menu.supportsEarbudSlots()) {
            var ears = skinLayout.earbudConnection().at(cx, cy);
            addEarbudButton(ears);
        }
        var source = skinLayout.control(RosewoodPlayerLayout.Control.SOURCE).at(cx, cy);
        this.sourceButton = this.addRenderableWidget(TransparentButton.builder(getSourceButtonText(), b -> {
            this.searchSource = (this.searchSource + 1) % 3;
            searchGeneration.invalidate();
            this.isSearching = false;
            b.setMessage(getSourceButtonText());
            this.statusMessage = Component.empty();
            providerAuth.setProvider(searchSource);
            updateTabVisibility();
            if (this.resultList != null)
                this.resultList.setResults(Collections.emptyList());
        }).pos(source.x(), source.y()).size(source.width(), source.height()).build());
        this.sourceButton.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal("切换网易云 / QQ / Apple 音源")));
        var input = skinLayout.searchField().at(cx, cy);
        this.searchBox = new EditBox(this.font, input.x() + 2, input.y() + Math.max(0, (input.height() - 9) / 2), Math.max(1, input.width() - 4), 12, Component.translatable("gui.mengsamanetmusic.music_player.search_placeholder"));
        this.searchBox.setBordered(false);
        this.searchBox.setMaxLength(50);
        this.searchBox.setTextColor(MusicPlayerSkin.primary());
        this.searchBox.setHint(Component.translatable("gui.mengsamanetmusic.music_player.search_hint").withStyle(ChatFormatting.ITALIC, ChatFormatting.DARK_GRAY));
        this.searchBox.setResponder(value -> {
            if (currentTab == TAB_PLAYLIST && playlistList != null)
                playlistList.applyUserFilter(value);
            if (currentTab == TAB_FAVORITES && favoriteList != null)
                favoriteList.applyFilter(value);
        });
        this.addWidget(this.searchBox);
        this.searchButton = addSkinButton(RosewoodPlayerLayout.Control.SEARCH, "搜索歌曲", b -> performSearch());
        var auth = skinLayout.control(RosewoodPlayerLayout.Control.AUTH).at(cx, cy);
        this.qqLoginButton = this.addRenderableWidget(providerAuth.createButton(auth.x(), auth.y(), auth.width(), auth.height()));
        providerAuth.setProvider(searchSource);
        this.tabSearchBtn = addNavigationButton(RosewoodPlayerLayout.Control.NAV_SEARCH, "歌曲搜索", TAB_SEARCH);
        this.tabPlaylistBtn = addNavigationButton(RosewoodPlayerLayout.Control.NAV_PLAYLIST, "设备歌单", TAB_PLAYLIST);
        this.tabFavoritesBtn = addNavigationButton(RosewoodPlayerLayout.Control.NAV_FAVORITES, "收藏", TAB_FAVORITES);
        this.tabLyricsBtn = addNavigationButton(RosewoodPlayerLayout.Control.NAV_LYRICS, "歌词", TAB_LYRICS);
        this.tabSettingsBtn = addNavigationButton(RosewoodPlayerLayout.Control.NAV_SETTINGS, "界面设置", TAB_SETTINGS);
        var content = skinLayout.content().at(cx, cy);
        var songContent = skinLayout.songContent().at(cx, cy);
        this.resultList = new SearchResultList(this.minecraft, songContent.width(), songContent.height(), songContent.y(), songContent.bottom(), ui.searchResultRowHeight);
        this.resultList.setX(content.x());
        this.addWidget(this.resultList);
        this.playlistList = new PlaylistList(this.minecraft, songContent.width(), songContent.height(), songContent.y(), songContent.bottom(), ui.playlistRowHeight);
        this.playlistList.setX(content.x());
        this.addWidget(this.playlistList);
        this.lyricList = new LyricList(this.minecraft, content.width(), content.height(), content.y(), content.bottom(), ui.lyricRowHeight);
        this.lyricList.setX(content.x());
        this.addWidget(this.lyricList);
        this.favoriteList = new FavoriteSongList(this.minecraft, songContent.width(), songContent.height(), songContent.y(), songContent.bottom(), ui.playlistRowHeight, this::activateFavorite, message -> this.statusMessage = message);
        this.favoriteList.setX(content.x());
        this.addWidget(this.favoriteList);
        initFavoriteTools(this::activateFavorite);
        var theme = skinLayout.settingsTheme().at(cx, cy);
        this.settingsThemeButton = this.addRenderableWidget(TransparentButton.builder(Component.literal("主题、背景与 HUD"), b -> openThemeSettings()).pos(theme.x(), theme.y()).size(theme.width(), theme.height()).build());
        this.broadcastButton = null;
        this.settingsEarbudButton = null;
        if (menu.supportsEarbudSlots()) {
            this.settingsEarbudButton = addEarbudButton(skinLayout.settingsEarbuds().at(cx, cy));
        }
        if (this.menu.isPlayerHandContext()) {
            var broadcast = skinLayout.settingsBroadcast().at(cx, cy);
            this.broadcastButton = this.addRenderableWidget(TransparentButton.builder(getBroadcastButtonText(), b -> handleButtonClick(MusicPlayerMenu.BUTTON_BROADCAST)).pos(broadcast.x(), broadcast.y()).size(broadcast.width(), broadcast.height()).build());
            this.broadcastButton.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal("向附近玩家广播音乐")));
        }
        var volume = skinLayout.control(RosewoodPlayerLayout.Control.VOLUME).at(cx, cy);
        var volumeButton = new MusicVolumeButton(volume.x(), volume.y(), volume.width(), volume.height());
        volumeButton.setSkinned(true);
        this.addRenderableWidget(volumeButton);
        this.prevButton = addSkinButton(RosewoodPlayerLayout.Control.PREVIOUS, "上一首", b -> handleButtonClick(MusicPlayerMenu.BUTTON_PREV));
        this.playButton = addSkinButton(RosewoodPlayerLayout.Control.PLAY, getPlayButtonText().getString(), b -> handleButtonClick(MusicPlayerMenu.BUTTON_PLAY));
        this.stopButton = addSkinButton(RosewoodPlayerLayout.Control.STOP, "停止播放", b -> handleButtonClick(MusicPlayerMenu.BUTTON_STOP));
        this.nextButton = addSkinButton(RosewoodPlayerLayout.Control.NEXT, "下一首", b -> handleButtonClick(MusicPlayerMenu.BUTTON_NEXT));
        this.modeButton = addSkinButton(RosewoodPlayerLayout.Control.MODE, getModeButtonText(this.menu.getPlayMode()).getString(), b -> handleButtonClick(MusicPlayerMenu.BUTTON_MODE));
        this.modeButton.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal("切换单曲 / 列表 / 随机播放")));
        addSkinButton(RosewoodPlayerLayout.Control.COVER, "查看当前歌曲歌词", b -> switchTab(TAB_LYRICS));
        initPagination();
        switchTab(initialTab);
    }

    private TransparentButton addEarbudButton(RosewoodPlayerLayout.Rect bounds) {
        var button = this.addRenderableWidget(TransparentButton.builder(Component.literal("耳机连接"), b -> ModNetwork.CHANNEL.sendToServer(new com.mengsama.mod.mengsamanetmusic.earbuds.EarbudActionPacket(0, com.mengsama.mod.mengsamanetmusic.earbuds.EarbudActionPacket.NONE, menu.containerId))).pos(bounds.x(), bounds.y()).size(bounds.width(), bounds.height()).build());
        button.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal("打开有线耳机、蓝牙左耳和蓝牙右耳的三个槽位")));
        return button;
    }

    private void returnToMaid() {
        if (returningToMaid || this.menu.getBoundEntityId() == null || this.menu.getBoundRuntimeEntityId() < 0 || this.menu.getBoundInstanceId() == null)
            return;
        playbackRequests.invalidate();
        returningToMaid = true;
        ModNetwork.CHANNEL.sendToServer(new ReturnToMaidGuiPacket(this.menu.getBoundEntityId(), this.menu.getBoundRuntimeEntityId(), this.menu.getBoundInstanceId()));
    }

    @Override
    public void onClose() {
        if (this.menu.getBoundEntityId() != null && !returningToMaid) {
             
             
            super.onClose();
            returnToMaid();
            return;
        }
        super.onClose();
    }

    @Override
    public void removed() {
        playbackRequests.invalidate();
         
        searchGeneration.invalidate();
        isSearching = false;
        MusicPlayerBackground.close();
        if (!openingSettings)
            super.removed();
    }

    private TransparentButton addNavigationButton(RosewoodPlayerLayout.Control control, String label, int tab) {
        return addSkinButton(control, label, b -> switchTab(tab));
    }

    public void prepareForChildScreen() {
        this.openingSettings = true;
    }

    private void openThemeSettings() {
        if (this.minecraft == null)
            return;
        ThemeSelectionScreen.open(this);
    }

    private void toggleFavorite(SongInfo song) {
        SongFavoriteActions.toggle(song, message -> this.statusMessage = message, () -> {
            if (this.favoriteList != null)
                this.favoriteList.refresh();
        });
    }

    private void activateFavorite(SongInfo song, boolean playNow) {
        if (song == null)
            return;
        if (!song.isValid()) {
            onSearchResultClicked(SongFavoriteActions.toSearchResult(song), playNow);
            return;
        }
        if (playNow)
            playbackRequests.invalidate();
        ModNetwork.CHANNEL.sendToServer(new PlayerAddSongPacket(song, playNow));
        this.statusMessage = Component.literal((playNow ? "正在播放：" : "已请求加入设备歌单：") + song.songName);
    }

    private void handleButtonClick(int buttonId) {
        if (buttonId == MusicPlayerMenu.BUTTON_PLAY || buttonId == MusicPlayerMenu.BUTTON_STOP || buttonId == MusicPlayerMenu.BUTTON_PREV || buttonId == MusicPlayerMenu.BUTTON_NEXT || (buttonId >= MusicPlayerMenu.BUTTON_SELECT_BASE && buttonId < MusicPlayerMenu.BUTTON_DELETE_BASE) || buttonId == MusicPlayerMenu.BUTTON_DELETE_BASE + this.menu.getPlayIndex()) {
            playbackRequests.invalidate();
        }
         
        if (buttonId == MusicPlayerMenu.BUTTON_PLAY) {
            String target = ClientMusicPlayback.authoritativeTarget(this.menu.getTargetId());
            boolean paused = ClientMusicPlayback.isPaused(target);
            if (this.menu.isPlaying() || paused)
                ClientMusicPlayback.setPaused(target, !paused);
        }
        if (this.minecraft != null && this.minecraft.gameMode != null) {
            this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, buttonId);
        }
    }

    protected void updateTabVisibility() {
         
         
        java.util.List<net.minecraft.client.gui.components.events.GuiEventListener> lists = java.util.Arrays.asList(this.resultList, this.playlistList, this.lyricList, this.favoriteList);
        if (this.getFocused() != null && lists.contains(this.getFocused())) {
            this.setFocused(null);
            this.setDragging(false);
        }
        for (var list : lists) if (list != null)
            this.removeWidget(list);
        if (currentTab == TAB_SEARCH && this.resultList != null)
            this.addWidget(this.resultList);
        else if (currentTab == TAB_PLAYLIST && this.playlistList != null)
            this.addWidget(this.playlistList);
        else if (currentTab == TAB_LYRICS && this.lyricList != null)
            this.addWidget(this.lyricList);
        else if (currentTab == TAB_FAVORITES && this.favoriteList != null)
            this.addWidget(this.favoriteList);
         
        boolean onlineSearch = currentTab == TAB_SEARCH;
        boolean searchVisible = onlineSearch || currentTab == TAB_PLAYLIST || currentTab == TAB_FAVORITES;
        if (this.settingsThemeButton != null)
            this.settingsThemeButton.visible = currentTab == TAB_SETTINGS;
        if (this.broadcastButton != null)
            this.broadcastButton.visible = currentTab == TAB_SETTINGS;
        if (this.settingsEarbudButton != null)
            this.settingsEarbudButton.visible = currentTab == TAB_SETTINGS;
        if (this.searchBox != null) {
            this.searchBox.visible = searchVisible;
            if (!searchVisible) {
                this.searchBox.setFocused(false);
                if (this.getFocused() == this.searchBox)
                    this.setFocused(null);
            }
            this.searchBox.setHint(Component.translatable(onlineSearch ? "gui.mengsamanetmusic.music_player.search_hint" : "gui.mengsamanetmusic.music_player.playlist_filter_hint").withStyle(ChatFormatting.ITALIC, ChatFormatting.DARK_GRAY));
        }
        if (this.sourceButton != null)
            this.sourceButton.visible = onlineSearch;
        if (this.searchButton != null)
            this.searchButton.visible = onlineSearch;
         
         
        if (this.qqLoginButton != null)
            this.qqLoginButton.visible = onlineSearch;
    }

    protected boolean isPlaying() {
        return this.menu.isPlaying();
    }

    private Component getSourceButtonText() {
        return Component.literal(switch(searchSource) {
            case 1 ->
                "QQ音乐";
            case 2 ->
                "Apple";
            default ->
                "网易云";
        });
    }

    private void performSearch() {
        if (this.searchBox == null)
            return;
        String query = this.searchBox.getValue().trim();
        if (query.isEmpty()) {
            this.statusMessage = Component.translatable("gui.mengsamanetmusic.music_player.search_empty");
            return;
        }
        this.isSearching = true;
        int selectedSource = this.searchSource;
        boolean qqSearch = selectedSource == 1;
        boolean appleSearch = selectedSource == 2;
        String queryToken = (qqSearch ? "qq:" : appleSearch ? "apple:" : "netease:") + query;
        long generation = searchGeneration.begin(queryToken);
        this.statusMessage = Component.translatable("gui.mengsamanetmusic.search.searching");
        com.mengsama.mod.mengsamanetmusic.api.MusicSearchService.search(selectedSource, query).whenComplete((results, error) -> Minecraft.getInstance().execute(() -> {
            if (!searchGeneration.isCurrent(generation, queryToken))
                return;
            this.isSearching = false;
            if (error != null) {
                this.statusMessage = Component.translatable("gui.mengsamanetmusic.search.search_failed");
                if (this.resultList != null)
                    this.resultList.setResults(Collections.emptyList());
            } else {
                if (this.resultList != null) {
                    this.resultList.setResults(results);
                    this.resultList.setScrollAmount(0.0D);
                }
                if (results == null || results.isEmpty()) {
                    this.statusMessage = Component.translatable("gui.mengsamanetmusic.search.no_result");
                } else {
                    this.statusMessage = Component.empty();
                    if (!qqSearch && !appleSearch)
                        hydrateSearchCovers(results, generation, queryToken);
                }
            }
        }));
    }

    private List<NetEaseSearchResult> parseSearchResults(String json) {
        return NetEaseApi.parseSearchResults(json);
    }

    private void hydrateSearchCovers(List<NetEaseSearchResult> results, long generation, String queryToken) {
        NetEaseSearchMetadataLoader.hydrateMissing(results, MengSamaNetMusic.NET_EASE_API).whenComplete((hydrated, error) -> Minecraft.getInstance().execute(() -> {
            if (error != null || !searchGeneration.isCurrent(generation, queryToken))
                return;
            if (this.resultList != null)
                this.resultList.replaceResults(hydrated);
        }));
    }

    private void onSearchResultClicked(NetEaseSearchResult result, boolean playNow) {
        if (result == null)
            return;
        long playbackToken = playNow ? playbackRequests.begin("play") : -1L;
        this.statusMessage = Component.translatable("gui.mengsamanetmusic.search.loading");
        CompletableFuture.supplyAsync(() -> {
            try {
                SongInfo song;
                if (result.isApple()) {
                    song = AppleMusicApi.toSong(result);
                } else if (result.isQq()) {
                    song = QqMusicUtils.resolveSong(result.getSongId(), VipCookieState.getEffectiveVipCookie(), 1);
                    if (song != null) {
                        song.source = "qq";
                        song.artists.clear();
                        if (result.getArtistName() != null && !result.getArtistName().isBlank()) {
                            song.artists.add(result.getArtistName());
                        }
                        song.providerId = result.getSongId();
                         
                         
                        if (result.getAlbumMid() != null && !result.getAlbumMid().isBlank()) {
                            song.albumMid = result.getAlbumMid();
                        }
                        if (result.getCoverUrl() != null && !result.getCoverUrl().isBlank()) {
                            song.coverUrl = result.getCoverUrl();
                            song.picUrl = result.getCoverUrl();
                        }
                        if (!result.getAlbumName().isBlank())
                            song.albumName = result.getAlbumName();
                        if (song.songTime <= 0)
                            song.songTime = result.getDuration();
                        song.normalizeIdentity();
                    }
                } else {
                    song = MengSamaNetMusic.NET_EASE_API.get163Song(result);
                }
                return song;
            } catch (Exception e) {
                MengSamaNetMusic.LOGGER.error("Failed to fetch song info", e);
                return null;
            }
        }, Util.backgroundExecutor()).thenAccept(song -> Minecraft.getInstance().execute(() -> {
            if (this.minecraft == null || this.minecraft.player == null || this.minecraft.player.containerMenu != this.menu)
                return;
            if (playNow && !playbackRequests.isCurrent(playbackToken, "play"))
                return;
            if (song != null && song.isValid()) {
                ModNetwork.CHANNEL.sendToServer(new PlayerAddSongPacket(song, playNow));
                this.statusMessage = playNow ? Component.literal("\u25B6 " + song.songName).withStyle(ChatFormatting.GREEN) : Component.literal("\u2713 " + song.songName).withStyle(ChatFormatting.AQUA);
            } else {
                this.statusMessage = Component.translatable("gui.mengsamanetmusic.search.get_info_error");
            }
        }));
    }

    protected boolean isPlaybackPaused() {
        return ClientMusicPlayback.isPaused(ClientMusicPlayback.authoritativeTarget(this.menu.getTargetId()));
    }

    private Component getPlayButtonText() {
        return Component.literal(isPlaying() && !isPlaybackPaused() ? "暂停" : "播放");
    }

    private Component getModeButtonText(PlayMode mode) {
        return switch(mode) {
            case LOOP ->
                Component.literal("单曲");
            case SEQUENTIAL ->
                Component.literal("列表");
            case RANDOM ->
                Component.literal("随机");
        };
    }

    private Component getBroadcastButtonText() {
        if (com.mengsama.mod.mengsamanetmusic.earbuds.EarbudSlots.installed(menu.getDevice()))
            return Component.literal("耳机已连接 · 私密播放");
        return Component.translatable(this.menu.isBroadcast() ? "gui.mengsamanetmusic.music_player.broadcast_on" : "gui.mengsamanetmusic.music_player.broadcast_off");
    }

    private SongInfo getSongInfoFromCd(ItemStack cd) {
        return cd.getItem() instanceof MusicListItem ? MusicListItem.getSongInfo(cd) : null;
    }

    static boolean samePlaylist(List<SongInfo> leftSongs, List<Integer> leftSlots, List<SongInfo> rightSongs, List<Integer> rightSlots) {
        if (!leftSlots.equals(rightSlots) || leftSongs.size() != rightSongs.size())
            return false;
        for (int i = 0; i < leftSongs.size(); i++) {
            SongInfo left = leftSongs.get(i);
            SongInfo right = rightSongs.get(i);
            if (!left.identityKey().equals(right.identityKey()) || !java.util.Objects.equals(left.songName, right.songName) || left.songTime != right.songTime || !java.util.Objects.equals(left.artists, right.artists))
                return false;
        }
        return true;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        if (this.playButton != null)
            this.playButton.setMessage(getPlayButtonText());
        if (this.modeButton != null)
            this.modeButton.setMessage(getModeButtonText(this.menu.getPlayMode()));
        if (this.broadcastButton != null) {
            this.broadcastButton.setMessage(getBroadcastButtonText());
            this.broadcastButton.active = this.menu.canUseHeadphones() && !com.mengsama.mod.mengsamanetmusic.earbuds.EarbudSlots.installed(menu.getDevice());
            this.broadcastButton.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(com.mengsama.mod.mengsamanetmusic.earbuds.EarbudSlots.installed(menu.getDevice()) ? "耳机槽已连接，强制私密播放" : this.menu.canUseHeadphones() ? "切换外放 / 耳机私听" : "佩戴头盔栏或 Curios 头饰栏的耳机后才能关闭外放")));
        }
        super.render(graphics, mouseX, mouseY, partialTicks);
        var titleRect = skinLayout.title().at(leftPos, topPos);
        int titleWidth = titleRect.width();
        if (this.backToMaidButton != null)
            titleWidth = this.backToMaidButton.getX() - titleRect.x() - 3;
        graphics.drawString(font, font.plainSubstrByWidth(this.title.getString(), Math.max(1, titleWidth)), titleRect.x(), titleRect.y(), MusicPlayerSkin.title(), false);
        if (this.searchBox != null)
            this.searchBox.render(graphics, mouseX, mouseY, partialTicks);
        var content = skinLayout.content().at(leftPos, topPos);
        if (currentTab == TAB_SEARCH && this.resultList != null)
            this.resultList.render(graphics, mouseX, mouseY, partialTicks);
        else if (currentTab == TAB_PLAYLIST && this.playlistList != null)
            this.playlistList.render(graphics, mouseX, mouseY, partialTicks);
        else if (currentTab == TAB_LYRICS && this.lyricList != null)
            this.lyricList.render(graphics, mouseX, mouseY, partialTicks);
        else if (currentTab == TAB_FAVORITES && this.favoriteList != null)
            this.favoriteList.render(graphics, mouseX, mouseY, partialTicks);
        else if (currentTab == TAB_SETTINGS) {
            graphics.drawCenteredString(font, "界面设置", content.x() + content.width() / 2, content.y() + (skinLayout.compactSettings() ? 2 : 8), MusicPlayerSkin.primary());
            if (content.height() >= 170) {
                graphics.drawCenteredString(font, "按钮随贴图缩放，位置固定", content.x() + content.width() / 2, content.bottom() - 28, MusicPlayerSkin.secondary());
                graphics.drawCenteredString(font, "F6：打开主题与 HUD 设置", content.x() + content.width() / 2, content.bottom() - 15, MusicPlayerSkin.secondary());
            }
        }
        if (currentTab != TAB_SETTINGS && currentTab != TAB_LYRICS && !this.statusMessage.getString().isEmpty()) {
            String status = font.plainSubstrByWidth(this.statusMessage.getString(), Math.max(1, content.width() - 12));
            graphics.drawCenteredString(font, status, content.x() + content.width() / 2, content.bottom() - 33, MusicPlayerSkin.secondary());
        }
        renderProgressBar(graphics, leftPos, topPos);
        renderNowPlaying(graphics, leftPos, topPos);
        this.renderTooltip(graphics, mouseX, mouseY);
    }

    protected String getPlaybackTarget() {
        return ClientMusicPlayback.authoritativeTarget(this.menu.getTargetId());
    }

    protected int getPlayingTick() {
        return ClientMusicPlayback.getTick(getPlaybackTarget());
    }

    protected SongInfo getPlayingSongInfo() {
        SongInfo active = ClientMusicPlayback.getSongInfo(getPlaybackTarget());
         
         
         
        return ClientLyricStore.selectGuiSong(active, this.menu.isPlaying(), this.menu.getSongInfo(this.menu.getPlayIndex()));
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && draggingProgress) {
            updateSeekPreview(mouseX, mouseY);
            draggingProgress = false;
            String targetId = getPlaybackTarget();
            ClientMusicPlayback.seekImmediately(targetId, previewSeekSecond);
            int entityId = this.menu.getBoundRuntimeEntityId() >= 0 ? this.menu.getBoundRuntimeEntityId() : this.minecraft != null && this.minecraft.player != null ? this.minecraft.player.getId() : -1;
            if (entityId >= 0)
                ModNetwork.CHANNEL.sendToServer(new SeekPlaybackPacket(entityId, null, getPlaybackTarget(), previewSeekSecond, getPlayingSongInfo() == null ? "" : getPlayingSongInfo().identityKey()));
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    protected net.minecraft.client.gui.components.events.GuiEventListener activeList() {
        return switch(currentTab) {
            case TAB_SEARCH ->
                this.resultList;
            case TAB_PLAYLIST ->
                this.playlistList;
            case TAB_LYRICS ->
                this.lyricList;
            case TAB_FAVORITES ->
                this.favoriteList;
            default ->
                null;
        };
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (handlePageKey(keyCode)) return true;
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_F6) {
            openThemeSettings();
            return true;
        }
        if (keyCode == InputConstants.KEY_ESCAPE && this.menu.getBoundEntityId() != null) {
            returnToMaid();
            return true;
        }
        InputConstants.Key key = InputConstants.getKey(keyCode, scanCode);
        if (this.minecraft != null && this.minecraft.options.keyInventory.isActiveAndMatches(key)) {
            if ((this.searchBox != null && this.searchBox.isFocused()) || isPageInputFocused())
                return true;
            this.onClose();
            return true;
        }
        if ((keyCode == 257 || keyCode == 335) && currentTab == TAB_SEARCH && this.searchBox != null && this.searchBox.isFocused()) {
            performSearch();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        providerAuth.tick();
        if (this.qqLoginButton != null)
            this.qqLoginButton.visible = currentTab == TAB_SEARCH && searchSource != 0;
        if (this.searchBox != null)
            {}
        if (currentTab == TAB_LYRICS && this.lyricList != null) {
             
             
            lyricRefreshCounter = 0;
            this.lyricList.refresh();
        }
        if (currentTab == TAB_PLAYLIST && this.playlistList != null) {
            lyricRefreshCounter++;
            if (lyricRefreshCounter >= 20) {
                lyricRefreshCounter = 0;
                this.playlistList.refresh();
            }
        }
    }

    @Override
    public void resize(Minecraft minecraft, int width, int height) {
        String searchValue = this.searchBox != null ? this.searchBox.getValue() : "";
        int oldTab = this.currentTab;
        super.resize(minecraft, width, height);
        if (this.searchBox != null)
            this.searchBox.setValue(searchValue);
        switchTab(oldTab);
    }

    private void removeSong(int slot) {
        if (slot == this.menu.getPlayIndex())
            playbackRequests.invalidate();
        ModNetwork.CHANNEL.sendToServer(new PlayerRemoveSongPacket(slot));
        if (this.playlistList != null)
            this.playlistList.refresh();
    }

    private class SearchResultList extends PagedSongList<SearchResultList.Entry> {

        public SearchResultList(Minecraft mc, int w, int h, int top, int bottom, int itemH) {
            super(mc, w, h, top, bottom, itemH);
            
            
        }

        @Override
        public int getRowWidth() {
            return this.getWidth() - 12;
        }

        @Override
        protected int getScrollbarPosition() {
            return this.getX() + this.getWidth() - 6;
        }

        public void setResults(List<NetEaseSearchResult> results) {
            replaceSongs(results == null ? List.of() : results.stream().map(Entry::new).toList(), true);
        }

        public void replaceResults(List<NetEaseSearchResult> results) {
            replaceSongs(results == null ? List.of() : results.stream().map(Entry::new).toList(), false);
        }

        private class Entry extends ObjectSelectionList.Entry<Entry> {

            private final NetEaseSearchResult result;

            private final SongInfo favoriteSong;

            private int rowX, rowY, rowW, rowH;

            public Entry(NetEaseSearchResult r) {
                this.result = r;
                this.favoriteSong = SongFavoriteActions.fromSearchResult(r);
            }

            @Override
            public void render(GuiGraphics g, int index, int y, int x, int ew, int eh, int mx, int my, boolean hovered, float pt) {
                this.rowX = x;
                this.rowY = y;
                this.rowW = ew;
                this.rowH = eh;
                MusicPlayerUiConfig.Values ui = MusicPlayerUiConfig.get();
                SongRowRenderer.renderSearch(g, font, result, x, y, ew, eh, hovered, ClientFavoriteStore.contains(favoriteSong));
            }

            @Override
            public boolean mouseClicked(double mx, double my, int button) {
                if (button == 0 && SongRowRenderer.hitFavorite(mx, my, rowX, rowY, rowW, rowH)) {
                    toggleFavorite(favoriteSong);
                    return true;
                }
                if (button == 0 && SongRowRenderer.hitAction(mx, my, rowX, rowY, rowW, rowH)) {
                    onSearchResultClicked(result, false);
                    return true;
                }
                return false;
            }

            @Override
            @NotNull
            public Component getNarration() {
                return Component.literal(result.getDisplayText());
            }
        }
    }

    private class PlaylistList extends PagedSongList<PlaylistList.Entry> {

        private final List<SongInfo> songs = new ArrayList<>();

        private final List<Integer> slotIndices = new ArrayList<>();

        public PlaylistList(Minecraft mc, int w, int h, int top, int bottom, int itemH) {
            super(mc, w, h, top, bottom, itemH);
            
            
        }

        @Override
        public int getRowWidth() {
            return this.getWidth() - 12;
        }

        @Override
        protected int getScrollbarPosition() {
            return this.getX() + this.getWidth() - 6;
        }

        public void refresh() {
            List<SongInfo> newSongs = new ArrayList<>();
            List<Integer> newSlots = new ArrayList<>();
            double oldScroll = this.getScrollAmount();
            int anchorSlot = visibleSlotAt(oldScroll);
            double anchorOffset = oldScroll - Math.floor(oldScroll / rowHeight()) * rowHeight();
            if (minecraft != null && minecraft.player != null) {
                 
                ItemStack item = menu.getDevice();
                if (!item.isEmpty()) {
                    var cds = MusicPlayerItem.loadAllCds(item);
                    for (int i = 0; i < cds.size(); i++) {
                        if (!cds.get(i).isEmpty()) {
                            SongInfo info = getSongInfoFromCd(cds.get(i));
                            if (info != null) {
                                newSongs.add(info);
                                newSlots.add(i);
                            }
                        }
                    }
                }
            }
            if (samePlaylist(songs, slotIndices, newSongs, newSlots))
                return;
            songs.clear();
            songs.addAll(newSongs);
            slotIndices.clear();
            slotIndices.addAll(newSlots);
            applyFilter(searchBox == null ? "" : searchBox.getValue(), oldScroll, anchorSlot, anchorOffset);
        }

        private int rowHeight() {
            return Math.max(1, MusicPlayerUiConfig.get().playlistRowHeight);
        }

        private int visibleSlotAt(double scroll) {
            int entryIndex = Math.max(0, (int) Math.floor(scroll / rowHeight()));
            return entryIndex < children().size() ? children().get(entryIndex).realSlot : -1;
        }

        public void applyUserFilter(String query) {
            goToPage(1);
            applyFilter(query, 0.0D, -1, 0.0D);
        }

        private void applyFilter(String query, double fallbackScroll, int anchorSlot, double anchorOffset) {
            var page = PlaylistWindow.filter(songs, slotIndices, query, fallbackScroll, anchorSlot, anchorOffset, rowHeight());
            replaceSongs(page.rows().stream().map(match -> new Entry(match.song(), match.slotIndex())).toList(), false);
            this.setScrollAmount(anchorSlot >= 0 ? Math.max(0, page.scroll() - pageOffset() * rowHeight()) : page.scroll());
        }

        private class Entry extends ObjectSelectionList.Entry<Entry> {

            private final SongInfo song;

            private final int realSlot;

            private int rowX, rowY, rowW, rowH;

            public Entry(SongInfo song, int realSlot) {
                this.song = song;
                this.realSlot = realSlot;
            }

            @Override
            public void render(GuiGraphics g, int index, int y, int x, int ew, int eh, int mx, int my, boolean hovered, float pt) {
                this.rowX = x;
                this.rowY = y;
                this.rowW = ew;
                this.rowH = eh;
                boolean isCurrent = realSlot == menu.getPlayIndex();
                MusicPlayerUiConfig.Values ui = MusicPlayerUiConfig.get();
                SongRowRenderer.renderPlaylist(g, font, song, realSlot, x, y, ew, eh, hovered, isCurrent, ClientFavoriteStore.contains(song));
            }

            @Override
            public boolean mouseClicked(double mx, double my, int button) {
                if (button == 0 && SongRowRenderer.hitFavorite(mx, my, rowX, rowY, rowW, rowH)) {
                    toggleFavorite(song);
                    return true;
                }
                if (button == 0 && SongRowRenderer.hitAction(mx, my, rowX, rowY, rowW, rowH)) {
                    removeSong(realSlot);
                    return true;
                }
                if (button == 0) {
                    handleButtonClick(MusicPlayerMenu.BUTTON_SELECT_BASE + realSlot);
                    return true;
                }
                return false;
            }

            @Override
            @NotNull
            public Component getNarration() {
                return Component.literal(song.songName != null ? song.songName : "Unknown");
            }
        }
    }

    @Override
    protected void refreshPlaylist() {
        if (playlistList != null)
            playlistList.refresh();
    }
}

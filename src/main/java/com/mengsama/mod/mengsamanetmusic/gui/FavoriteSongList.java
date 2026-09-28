package com.mengsama.mod.mengsamanetmusic.gui;

import com.mengsama.mod.mengsamanetmusic.api.SongInfo;
import com.mengsama.mod.mengsamanetmusic.client.ClientFavoriteStore;
import com.mengsama.mod.mengsamanetmusic.util.AsyncIoExecutor;
import com.mengsama.mod.mengsamanetmusic.util.PlaylistFilter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.IntStream;

 
final class FavoriteSongList extends PagedSongList<FavoriteSongList.Entry> {
    private final BiConsumer<SongInfo, Boolean> activate;
    private final Consumer<Component> feedback;
    private List<SongInfo> songs = List.of();
    private String filter = "";
    private boolean loading;
    private long refreshGeneration;

    FavoriteSongList(Minecraft mc, int width, int height, int top, int bottom, int itemHeight,
                     BiConsumer<SongInfo, Boolean> activate, Consumer<Component> feedback) {
        super(mc, width, height, top, bottom, itemHeight);
        this.activate = activate;
        this.feedback = feedback;
        
        
        refresh();
    }

    @Override public int getRowWidth() { return getWidth() - 12; }
    @Override protected int getScrollbarPosition() { return getX() + getWidth() - 6; }

    void refresh() {
        loading = true;
        long generation = ++refreshGeneration;
        AsyncIoExecutor.supplyAsync(ClientFavoriteStore::snapshot).whenComplete((snapshot, error) ->
                Minecraft.getInstance().execute(() -> {
                    if (generation != refreshGeneration) return;
                    loading = false;
                    if (error != null) {
                        feedback.accept(Component.literal("收藏读取失败，请稍后重试"));
                        return;
                    }
                    songs = snapshot;
                    applyFilter(filter);
                    var status = ClientFavoriteStore.status();
                    if (!status.success()) feedback.accept(Component.literal(status.message()));
                }));
    }

    void applyFilter(String query) {
        String nextFilter = query == null ? "" : query;
        boolean reset = !filter.equals(nextFilter);
        filter = nextFilter;
        double scroll = getScrollAmount();
        var indices = IntStream.range(0, songs.size()).boxed().toList();
        replaceSongs(PlaylistFilter.filter(songs, indices, filter).stream().map(match -> new Entry(match.song())).toList(), reset);
        if (!reset) setScrollAmount(scroll);
    }

    @Override public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderWidget(graphics, mouseX, mouseY, partialTick);
        if (getItemCount() == 0) {
            String message = loading ? "正在读取收藏…" : songs.isEmpty()
                    ? "点击歌曲右侧 ☆ 即可收藏" : "没有匹配的收藏歌曲";
            graphics.drawCenteredString(minecraft.font, message, getX() + getWidth() / 2,
                    getY() + 18, MusicPlayerSkin.secondary());
        }
    }

    final class Entry extends ObjectSelectionList.Entry<Entry> {
        private final SongInfo song;
        private int x, y, width, height;
        Entry(SongInfo song) { this.song = song; }

        @Override public void render(GuiGraphics graphics, int index, int y, int x, int width, int height,
                                     int mouseX, int mouseY, boolean hovered, float partialTick) {
            this.x = x; this.y = y; this.width = width; this.height = height;
            SongRowRenderer.renderFavorite(graphics, minecraft.font, song, x, y, width, height,
                    hovered);
        }

        @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (button != 0) return false;
            if (SongRowRenderer.hitFavorite(mouseX, mouseY, x, y, width, height)) {
                SongFavoriteActions.toggle(song, feedback, FavoriteSongList.this::refresh);
            } else {
                activate.accept(song.clone(), !SongRowRenderer.hitAction(mouseX, mouseY, x, y, width, height));
            }
            return true;
        }

        @Override public @NotNull Component getNarration() {
            return Component.literal(song.songName + "，点击播放；右侧加入歌单或取消收藏");
        }
    }
}

package com.mengsama.mod.mengsamanetmusic.gui;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ObjectSelectionList;

 
abstract class PagedSongList<E extends ObjectSelectionList.Entry<E>> extends ObjectSelectionList<E> {
    private final SongPagination<E> pagination = new SongPagination<>();
    protected PagedSongList(Minecraft client, int width, int height, int top, int bottom, int rowHeight) {
        super(client, width, height, top, bottom, rowHeight);
    }
    protected final void replaceSongs(List<E> rows, boolean reset) {
        int oldPage = page();
        double scroll = getScrollAmount();
        pagination.replace(rows, reset);
        rebuild();
        setScrollAmount(reset || oldPage != page() ? 0 : scroll);
    }
    final int page() { return pagination.page(); }
    final int pageCount() { return pagination.pages(); }
    final int songCount() { return pagination.size(); }
    protected final int pageOffset() { return pagination.offset(); }
    final void goToPage(int requested) {
        int old = page();
        pagination.goTo(requested);
        if (old == page()) return;
        rebuild();
        setScrollAmount(0);
    }
    private void rebuild() {
        clearEntries();
        setSelected(null);
        for (E entry : pagination.visible()) addEntry(entry);
    }
}

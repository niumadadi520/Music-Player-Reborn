package com.mengsama.mod.mengsamanetmusic.gui;

import java.util.List;

 
final class SongPagination<T> {
    static final int PAGE_SIZE = 20;
    private List<T> entries = List.of();
    private int page = 1;
    void replace(List<T> values, boolean reset) {
        entries = values == null ? List.of() : List.copyOf(values);
        goTo(reset ? 1 : page);
    }
    int page() { return page; }
    int pages() { return entries.isEmpty() ? 1 : 1 + (entries.size() - 1) / PAGE_SIZE; }
    int size() { return entries.size(); }
    int offset() { return (page - 1) * PAGE_SIZE; }
    void goTo(int requested) { page = Math.max(1, Math.min(pages(), requested)); }
    List<T> visible() { return entries.subList(offset(), Math.min(entries.size(), offset() + PAGE_SIZE)); }
}

package com.mengsama.mod.mengsamanetmusic.gui.theme;

 
public final class ThemePages {
    public static final int SIZE=6;
    private ThemePages() {}
    public static int count(){return (MenuTheme.values().length+SIZE-1)/SIZE;}
    public static int clamp(int page){return Math.max(0,Math.min(count()-1,page));}
    public static int first(int page){return clamp(page)*SIZE;}
    public static int end(int page){return Math.min(first(page)+SIZE,MenuTheme.values().length);}
    public static int containing(MenuTheme theme){return theme.ordinal()/SIZE;}
}

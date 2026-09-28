package com.mengsama.mod.mengsamanetmusic.api;

public enum NetEaseMusicLevel {
    STANDARD,
    HIGHER,
    EXHIGH,
    LOSSLESS,
    HIRES;

    public String getTranslationKey() {
        return "command.mengsamanetmusic.level." + this.toString().toLowerCase();
    }
}

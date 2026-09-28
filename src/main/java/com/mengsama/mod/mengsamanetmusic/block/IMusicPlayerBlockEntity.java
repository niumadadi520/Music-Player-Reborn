package com.mengsama.mod.mengsamanetmusic.block;

import com.mengsama.mod.mengsamanetmusic.api.SongInfo;
import com.mengsama.mod.mengsamanetmusic.util.PlayMode;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.items.ItemStackHandler;

public interface IMusicPlayerBlockEntity {
    ItemStackHandler getPlayerInv();
    int getPlayIndex();
    void setPlayIndex(int index);
    PlayMode getPlayMode();
    void setPlayMode(PlayMode mode);
    boolean isPlay();
    void setPlay(boolean play);
    boolean isPaused();
    void setPaused(boolean paused);
    int getCurrentTime();
    void setCurrentTime(int time);
    ItemStack getCurrentCd();
    void advanceToNext();
    void markDirty();
    void setPlayToClient(SongInfo info);
    default void setPlayToClient(SongInfo info, int startSecond) { setPlayToClient(info); }
    default void seekToClient(SongInfo info, int startSecond, boolean paused) { setPlayToClient(info, startSecond); }
    Level getLevel();
    net.minecraft.core.BlockPos getBlockPos();
    String blockTargetId();
}

package com.mengsama.mod.mengsamanetmusic.block;

import net.minecraft.nbt.CompoundTag;

 
public final class DevicePlaybackClock {
    private enum Activity { STOPPED, PLAYING, PAUSED }
    private Activity activity = Activity.STOPPED;
    private int remaining;
    private boolean completionPending;
    private boolean powered;
    public boolean playing() { return activity != Activity.STOPPED; }
    public boolean paused() { return activity == Activity.PAUSED; }
    public int remaining() { return remaining; }
    public boolean powered() { return powered; }
    public void powered(boolean value) { powered = value; }
    public void playing(boolean value) { activity = value ? (paused() ? Activity.PAUSED : Activity.PLAYING) : Activity.STOPPED; }
    public void paused(boolean value) { if (playing()) activity = value ? Activity.PAUSED : Activity.PLAYING; }
    public void remaining(int value) { remaining = Math.max(0, value); completionPending = remaining > 0; }
    public void decrement() { if (remaining > 0) remaining--; }
    public boolean advance() {
        if (activity != Activity.PLAYING) return false;
        decrement();
        if (remaining != 0 || !completionPending) return false;
        completionPending = false;
        return true;
    }
    public void clear() { activity = Activity.STOPPED; remaining(0); }
    public void read(CompoundTag tag) {
        activity = !tag.getBoolean("IsPlay") ? Activity.STOPPED : tag.getBoolean("IsPaused") ? Activity.PAUSED : Activity.PLAYING;
        remaining(tag.getInt("CurrentTime"));
        powered = tag.getBoolean("RedStoneSignal");
    }
    public void write(CompoundTag tag) {
        tag.putBoolean("IsPlay", playing());
        tag.putBoolean("IsPaused", paused());
        tag.putInt("CurrentTime", remaining);
        tag.putBoolean("RedStoneSignal", powered);
    }
}

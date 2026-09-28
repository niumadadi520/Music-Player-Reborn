package com.mengsama.mod.mengsamanetmusic.block;

import software.bernie.geckolib.core.animation.Animation;
import software.bernie.geckolib.core.animation.RawAnimation;

 
public final class PlacedMusicAnimations {
    private static final RawAnimation WALKMAN_IDLE = RawAnimation.begin().thenLoop("animation.pink_walkman.idle");
    private static final RawAnimation WALKMAN_PLAY = RawAnimation.begin().thenLoop("animation.pink_walkman.play");
    private static final RawAnimation GRAMOPHONE_IDLE = RawAnimation.begin().thenLoop("animation.rose_gramophone.idle");
     
    private static final RawAnimation GRAMOPHONE_PLAY = RawAnimation.begin()
            .then("animation.rose_gramophone.start", Animation.LoopType.PLAY_ONCE)
            .thenLoop("animation.rose_gramophone.play");

    private PlacedMusicAnimations() {}

    public static RawAnimation walkman(boolean playing, boolean paused) {
        return playing && !paused ? WALKMAN_PLAY : WALKMAN_IDLE;
    }

    public static RawAnimation gramophone(boolean playing, boolean paused) {
        return playing && !paused ? GRAMOPHONE_PLAY : GRAMOPHONE_IDLE;
    }
}

package com.mengsama.mod.mengsamanetmusic.block;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

 
public enum WalkmanControl {
    PREVIOUS("previous_track", "上一首"), PLAY_PAUSE("play_pause", "播放 / 暂停"), NEXT("next_track", "下一首");
    public final String animation;
    public final String label;
    WalkmanControl(String animation, String label) { this.animation = animation; this.label = label; }

    public static WalkmanControl at(Direction facing, Direction hitFace, Vec3 hitWithinBlock) {
        if (facing != hitFace || !facing.getAxis().isHorizontal()) return null;
        double dx = hitWithinBlock.x - .5, dz = hitWithinBlock.z - .5;
        double localX = switch (facing) {
            case NORTH -> dx; case SOUTH -> -dx; case WEST -> -dz; case EAST -> dz;
            default -> Double.NaN;
        };
        double x = -localX * 16, y = hitWithinBlock.y * 16;
        if (!Double.isFinite(x) || !Double.isFinite(y) || y < 1.55 || y > 2.9) return null;
        if (x >= -3.28 && x <= -1.18) return PREVIOUS;
        if (x >= -1.05 && x <= 1.05) return PLAY_PAUSE;
        if (x >= 1.18 && x <= 3.28) return NEXT;
        return null;
    }
}

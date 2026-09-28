package com.mengsama.mod.mengsamanetmusic.block;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class WalkmanControlTest {
    @Test void alignsWithAllFourGeckoBlockRotations() {
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            assertEquals(WalkmanControl.PREVIOUS, WalkmanControl.at(facing, facing, world(facing, -2.23, 2.2)));
            assertEquals(WalkmanControl.PLAY_PAUSE, WalkmanControl.at(facing, facing, world(facing, 0, 2.2)));
            assertEquals(WalkmanControl.NEXT, WalkmanControl.at(facing, facing, world(facing, 2.23, 2.2)));
        }
    }
    @Test void rearTopAndBodyAreNotButtons() {
        assertNull(WalkmanControl.at(Direction.NORTH, Direction.SOUTH, world(Direction.NORTH, 0, 2.2)));
        assertNull(WalkmanControl.at(Direction.NORTH, Direction.UP, world(Direction.NORTH, 0, 2.2)));
        assertNull(WalkmanControl.at(Direction.NORTH, Direction.NORTH, world(Direction.NORTH, 0, 6)));
        assertNull(WalkmanControl.at(Direction.NORTH, Direction.NORTH, world(Direction.NORTH, 1.12, 2.2)));
        assertNull(WalkmanControl.at(Direction.NORTH, Direction.NORTH, new Vec3(Double.NaN, .1, .3)));
    }
    private Vec3 world(Direction facing, double modelX, double y) {
        double x = -modelX / 16, z = -2.85 / 16;
        return switch(facing) {
            case SOUTH -> new Vec3(.5-x,y/16,.5-z);
            case WEST -> new Vec3(.5+z,y/16,.5-x);
            case EAST -> new Vec3(.5-z,y/16,.5+x);
            default -> new Vec3(.5+x,y/16,.5+z);
        };
    }
}

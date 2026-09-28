package com.mengsama.mod.mengsamanetmusic.karaoke;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import com.mengsama.mod.mengsamanetmusic.init.ModBlocks;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

 
public final class KaraokeStandPlacement {
    private KaraokeStandPlacement() {}
    public static boolean ownsTop(Level level, BlockPos base, BlockPos upper) {
        return upper.equals(base.above()) && level.getBlockState(upper).getBlock() instanceof KaraokeStandTopBlock
                && level.getBlockState(base).getBlock() instanceof KaraokeDeviceBlock block && block.isStand();
    }
    public static boolean canReserveTop(Level level, BlockPos base) {
        BlockPos upper = base.above();
        return !level.isOutsideBuildHeight(upper) && level.hasChunkAt(upper) && (ownsTop(level, base, upper)
                || level.getBlockState(upper).canBeReplaced() && level.getBlockEntity(upper) == null);
    }
     
    public static boolean ensureTop(Level level, BlockPos base) {
        if (!canReserveTop(level, base)) return false;
        return ownsTop(level, base, base.above()) || level.setBlock(base.above(), ModBlocks.PINK_MICROPHONE_STAND_TOP.get().defaultBlockState(), Block.UPDATE_ALL);
    }
    public static boolean hasSpace(Level level, BlockPos pos, VoxelShape localShape) {
        VoxelShape worldShape = localShape.move(pos.getX(), pos.getY(), pos.getZ());
        var box = worldShape.bounds();
        for (BlockPos other : BlockPos.betweenClosed((int)Math.floor(box.minX), (int)Math.floor(box.minY), (int)Math.floor(box.minZ),
                (int)Math.floor(box.maxX - 1E-6), (int)Math.floor(box.maxY - 1E-6), (int)Math.floor(box.maxZ - 1E-6))) {
            if (other.equals(pos)) continue;
            if (level.isOutsideBuildHeight(other) || !level.hasChunkAt(other)) return false;
            if (ownsTop(level, pos, other)) continue;
            VoxelShape obstruction = level.getBlockState(other).getCollisionShape(level, other);
            if (!obstruction.isEmpty() && Shapes.joinIsNotEmpty(worldShape,
                    obstruction.move(other.getX(), other.getY(), other.getZ()), BooleanOp.AND)) return false;
        }
        return true;
    }
}

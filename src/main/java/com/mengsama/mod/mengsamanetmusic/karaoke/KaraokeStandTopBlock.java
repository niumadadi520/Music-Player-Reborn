package com.mengsama.mod.mengsamanetmusic.karaoke;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

 
public final class KaraokeStandTopBlock extends Block {
    private static final VoxelShape[][] SHAPES = createShapes();
    public KaraokeStandTopBlock() {
        super(Properties.of().strength(1F).sound(SoundType.WOOD).noOcclusion().noLootTable().dynamicShape()
                .pushReaction(PushReaction.BLOCK));
    }
    private static KaraokeDeviceBlock base(BlockGetter level, BlockPos pos) {
        return level.getBlockState(pos.below()).getBlock() instanceof KaraokeDeviceBlock block && block.isStand() ? block : null;
    }
    private static VoxelShape[][] createShapes() {
        VoxelShape[][] result = new VoxelShape[2][4];
        for (Direction facing : Direction.Plane.HORIZONTAL) for (int occupied = 0; occupied < 2; occupied++)
            result[occupied][facing.get2DDataValue()] = Shapes.join(KaraokeDeviceBlock.standShape(facing, occupied == 1).move(0, -1, 0),
                    Shapes.block(), BooleanOp.AND).optimize();
        return result;
    }
    @Override public RenderShape getRenderShape(BlockState state) { return RenderShape.INVISIBLE; }
    @Override public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (base(level, pos) == null) return Shapes.empty();
        BlockState below = level.getBlockState(pos.below());
        int occupied = level.getBlockEntity(pos.below()) instanceof KaraokeBlockEntity device && device.hasMicrophone() ? 1 : 0;
        int direction = below.getValue(KaraokeDeviceBlock.FACING).get2DDataValue();
        return SHAPES[occupied][direction];
    }
    @Override public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                           InteractionHand hand, BlockHitResult hit) {
        KaraokeDeviceBlock lower = base(level, pos);
        if (lower == null) return InteractionResult.PASS;
        BlockPos bottom = pos.below();
        if (!level.isClientSide && !level.mayInteract(player, bottom)) return InteractionResult.FAIL;
        return lower.use(level.getBlockState(bottom), level, bottom, player, hand,
                new BlockHitResult(hit.getLocation(), hit.getDirection(), bottom, hit.isInside()));
    }
    @Override public ItemStack getCloneItemStack(BlockGetter level, BlockPos pos, BlockState state) {
        KaraokeDeviceBlock lower = base(level, pos);
        return lower == null ? ItemStack.EMPTY : lower.getCloneItemStack(level, pos.below(), level.getBlockState(pos.below()));
    }
    @Override public BlockState updateShape(BlockState state, Direction side, BlockState neighbor, LevelAccessor level,
                                            BlockPos pos, BlockPos neighborPos) {
        if (side == Direction.DOWN && base(level, pos) == null) return Blocks.AIR.defaultBlockState();
        return super.updateShape(state, side, neighbor, level, pos, neighborPos);
    }
    @Override public void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moving) {
        if (!level.isClientSide && base(level, pos) == null) level.scheduleTick(pos, this, 1);
    }
    @Override public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (base(level, pos) == null) level.removeBlock(pos, false);
    }
    @Override public void onRemove(BlockState state, Level level, BlockPos pos, BlockState replacement, boolean moving) {
        if (!state.is(replacement.getBlock()) && !level.isClientSide && !level.restoringBlockSnapshots && base(level, pos) != null)
            level.destroyBlock(pos.below(), true);
        super.onRemove(state, level, pos, replacement, moving);
    }
}

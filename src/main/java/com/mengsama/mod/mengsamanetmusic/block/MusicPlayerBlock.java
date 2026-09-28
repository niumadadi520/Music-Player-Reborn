package com.mengsama.mod.mengsamanetmusic.block;

import com.mengsama.mod.mengsamanetmusic.init.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.*;

 
public class MusicPlayerBlock extends MusicDeviceBlock {
    public static final BooleanProperty CYCLE_DISABLE = BooleanProperty.create("cycle_disable");
    private static final VoxelShape BOUNDS = Block.box(2, 0, 2, 14, 6, 14);
    public MusicPlayerBlock() { registerDefaultState(defaultBlockState().setValue(CYCLE_DISABLE, true)); }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> fields) {
        super.createBlockStateDefinition(fields); fields.add(CYCLE_DISABLE);
    }
    @Override public BlockEntity newBlockEntity(BlockPos position, BlockState state) { return new MusicPlayerBlockEntity(position, state); }
    @Override protected BlockEntityType<?> deviceType() { return ModBlockEntities.MUSIC_PLAYER.get(); }
    @Override protected String menuTitle() { return "block.mengsamanetmusic.music_player"; }
    @Override protected boolean repeats(BlockState state) { return !state.getValue(CYCLE_DISABLE); }
    @Override public java.util.List<net.minecraft.world.item.ItemStack> getDrops(BlockState state,
            net.minecraft.world.level.storage.loot.LootParams.Builder context) {
        var drops = super.getDrops(state, context);
        var entity = context.getOptionalParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.BLOCK_ENTITY);
        if (entity instanceof MusicDeviceEntity device) {
            var saved = device.saveWithoutMetadata();
            saved.putBoolean("IsPlay", false);
            saved.putBoolean("IsPaused", false);
            saved.putInt("CurrentTime", 0);
            for (var item : drops) if (item.is(asItem())) item.addTagElement("BlockEntityTag", saved.copy());
        }
        return drops;
    }
    @Override public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext collision) { return BOUNDS; }
}

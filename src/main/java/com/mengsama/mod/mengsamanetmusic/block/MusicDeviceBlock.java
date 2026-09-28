package com.mengsama.mod.mengsamanetmusic.block;

import com.mengsama.mod.mengsamanetmusic.gui.MusicPlayerPlaylistMenu;
import com.mengsama.mod.mengsamanetmusic.item.MusicListItem;
import net.minecraft.core.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.network.NetworkHooks;

 
@SuppressWarnings("deprecation")
public abstract class MusicDeviceBlock extends HorizontalDirectionalBlock implements EntityBlock {
    protected MusicDeviceBlock() {
        super(Properties.of().sound(SoundType.WOOD).strength(0.5F).noOcclusion());
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.SOUTH));
    }
    protected abstract BlockEntityType<?> deviceType();
    protected abstract String menuTitle();
    protected boolean repeats(BlockState state) { return true; }
    protected boolean acceptsPlaylist(Player player) { return true; }
    protected void beforeOpen(ServerPlayer player, MusicDeviceEntity device) { }
    protected boolean pressDeviceControl(BlockState state, Level level, BlockPos pos, Player player,
                                         BlockHitResult hit, MusicDeviceEntity device) { return false; }
    protected void releaseContents(Level level, BlockPos pos, MusicDeviceEntity device, boolean moving) { }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> fields) { fields.add(FACING); }
    @Override public BlockState getStateForPlacement(BlockPlaceContext placement) { return defaultBlockState().setValue(FACING, placement.getHorizontalDirection().getOpposite()); }
    @Override public RenderShape getRenderShape(BlockState state) { return RenderShape.ENTITYBLOCK_ANIMATED; }
    @Override public boolean hasAnalogOutputSignal(BlockState state) { return true; }
    @Override public int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof MusicDeviceEntity device) || device.getCurrentCd().isEmpty()) return 0;
        return device.isPlay() ? 15 : 7;
    }
    @Override public void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbor, BlockPos source, boolean moving) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof MusicDeviceEntity device)
            device.powerChanged(level.hasNeighborSignal(pos));
    }
    @Override public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND || !(level.getBlockEntity(pos) instanceof MusicDeviceEntity device)) return InteractionResult.PASS;
        if (!player.isSpectator() && pressDeviceControl(state, level, pos, player, hit, device))
            return InteractionResult.sidedSuccess(level.isClientSide);
        if (player instanceof ServerPlayer server) {
            if (!player.isSpectator() && acceptsPlaylist(player) && insertOne(device, player)) return InteractionResult.SUCCESS;
            beforeOpen(server, device);
            NetworkHooks.openScreen(server, new SimpleMenuProvider(
                    (id, inventory, owner) -> new MusicPlayerPlaylistMenu(id, inventory, device), Component.translatable(menuTitle())),
                    buffer -> buffer.writeBlockPos(pos));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
    private static boolean insertOne(MusicDeviceEntity device, Player owner) {
        ItemStack held = owner.getMainHandItem();
        if (!(held.getItem() instanceof MusicListItem)) return false;
        ItemStack offered = held.copyWithCount(1);
        var inventory = device.getPlayerInv();
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            if (!inventory.insertItem(slot, offered, true).isEmpty()) continue;
            if (!inventory.insertItem(slot, offered, false).isEmpty()) continue;
            if (!owner.isCreative()) held.shrink(1);
            return true;
        }
        return false;
    }
    @Override public void onRemove(BlockState oldState, Level level, BlockPos pos, BlockState replacement, boolean moving) {
        if (!level.isClientSide && oldState.getBlock() != replacement.getBlock() && level.getBlockEntity(pos) instanceof MusicDeviceEntity device) {
            device.stopForUnload();
            releaseContents(level, pos, device, moving);
        }
        super.onRemove(oldState, level, pos, replacement, moving);
    }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide || type != deviceType()) return null;
        return (world, position, actualState, entity) -> ((MusicDeviceEntity)entity).tickPlayback(repeats(actualState));
    }
}

package com.mengsama.mod.mengsamanetmusic.block;

import com.mengsama.mod.mengsamanetmusic.platform.ItemData;
import com.mengsama.mod.mengsamanetmusic.init.ModItems;
import com.mengsama.mod.mengsamanetmusic.init.ModBlockEntities;
import com.mengsama.mod.mengsamanetmusic.util.PlayMode;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

@SuppressWarnings("deprecation")
public class PortableMusicPlayerBlock extends MusicDeviceBlock {
    @Override protected com.mojang.serialization.MapCodec<? extends PortableMusicPlayerBlock> codec() { return com.mojang.serialization.MapCodec.unit(this); }


    protected static final VoxelShape SHAPE_NORTH = Block.box(2.3, 0, 5.15, 13.7, 12, 10.85);
    protected static final VoxelShape SHAPE_SOUTH = Block.box(2.3, 0, 5.15, 13.7, 12, 10.85);
    protected static final VoxelShape SHAPE_EAST = Block.box(5.15, 0, 2.3, 10.85, 12, 13.7);
    protected static final VoxelShape SHAPE_WEST = Block.box(5.15, 0, 2.3, 10.85, 12, 13.7);

    @Override public BlockEntity newBlockEntity(BlockPos position, BlockState state) { return new PortableMusicPlayerBlockEntity(position, state); }
    @Override protected BlockEntityType<?> deviceType() { return ModBlockEntities.PORTABLE_MUSIC_PLAYER.get(); }
    @Override protected String menuTitle() { return "item.mengsamanetmusic.music_player"; }
    @Override protected boolean acceptsPlaylist(Player player) { return player.isShiftKeyDown(); }
    @Override protected void beforeOpen(ServerPlayer player, MusicDeviceEntity device) {
        if (((PortableMusicPlayerBlockEntity)device).hasConnectedEarbuds()) player.displayClientMessage(
                Component.literal("耳机仍连接在随身听里；拿起后可私听，取下耳机后可放置外放"), false);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide) return;
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof PortableMusicPlayerBlockEntity player) {
            CompoundTag tag = ItemData.nullable(stack);
            player.retainItemData(tag);
            if (placer instanceof ServerPlayer owner) {
                com.mengsama.mod.mengsamanetmusic.earbuds.EarbudSessions.disconnect(owner,"随身听已放下，一起听已结束");
                com.mengsama.mod.mengsamanetmusic.item.MusicPlayerItem.stopForTransfer(stack, owner);
            }
            if (tag != null) {

                if (tag.contains("Item")) {
                    var songs = com.mengsama.mod.mengsamanetmusic.item.MusicPlayerItem.loadAllCds(stack);
                    for (int slot = 0; slot < songs.size(); slot++) player.getPlayerInv().setStackInSlot(slot, songs.get(slot).copy());
                }
                if (tag.contains("PlayIndex")) {
                    player.setPlayIndex(tag.getInt("PlayIndex"));
                }
                if (tag.contains("PlayMode")) {
                    player.setPlayMode(PlayMode.getMode(tag.getInt("PlayMode")));
                }
            }
        }
    }

    @Override protected boolean pressDeviceControl(BlockState state, Level level, BlockPos pos, Player owner,
                                                   BlockHitResult hit, MusicDeviceEntity device) {
        if (!owner.isShiftKeyDown() || !(device instanceof PortableMusicPlayerBlockEntity walkman)) return false;
        WalkmanControl control = WalkmanControl.at(state.getValue(FACING), hit.getDirection(),
                hit.getLocation().subtract(net.minecraft.world.phys.Vec3.atLowerCornerOf(pos)));
        if (control == null) return false;
        if (!level.isClientSide) walkman.pressControl(control);
        return true;
    }

    @Override protected void releaseContents(Level level, BlockPos pos, MusicDeviceEntity device, boolean moving) {
        if (moving) return;
        PortableMusicPlayerBlockEntity walkman = (PortableMusicPlayerBlockEntity)device;
        ItemStack dropped = new ItemStack(ModItems.MUSIC_PLAYER.get());
        dropped.applyComponents(walkman.collectComponents());
        CompoundTag data = walkman.retainedItemData();
        data.putBoolean("IsPlay", false); data.putBoolean("IsPaused", false);
        data.putInt("CurrentTime", 0); data.remove("AutoAdvanceArmed");
        data.put("Item", walkman.getPlayerInv().serializeNBT(com.mengsama.mod.mengsamanetmusic.platform.GameRegistries.lookup()));
        data.putInt("PlayIndex", walkman.getPlayIndex());
        data.putInt("PlayMode", walkman.getPlayMode().ordinal());
        ItemData.set(dropped, data);
        Block.popResource(level, pos, dropped);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter worldIn, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(FACING)) {
            case NORTH -> SHAPE_NORTH;
            case SOUTH -> SHAPE_SOUTH;
            case EAST -> SHAPE_EAST;
            case WEST -> SHAPE_WEST;
            default -> SHAPE_NORTH;
        };
    }

}

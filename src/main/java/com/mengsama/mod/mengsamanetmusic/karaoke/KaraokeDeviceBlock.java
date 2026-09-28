package com.mengsama.mod.mengsamanetmusic.karaoke;

import com.mengsama.mod.mengsamanetmusic.block.MusicPlayerBlock;
import com.mengsama.mod.mengsamanetmusic.block.MusicPlayerBlockEntity;
import com.mengsama.mod.mengsamanetmusic.gui.MusicPlayerPlaylistMenu;
import com.mengsama.mod.mengsamanetmusic.init.ModBlockEntities;
import com.mengsama.mod.mengsamanetmusic.item.MusicPlayerItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.*;
import java.util.UUID;

public final class KaraokeDeviceBlock extends HorizontalDirectionalBlock implements EntityBlock {
    private static final VoxelShape[][] STAND_SHAPES = createStandShapes();
    private final String modelName;
    private final boolean speaker;
    public KaraokeDeviceBlock(String modelName, boolean speaker) {
        super(Properties.of().strength(1F).sound(SoundType.WOOD).noOcclusion().noLootTable().dynamicShape());
        this.modelName = modelName; this.speaker = speaker;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(MusicPlayerBlock.CYCLE_DISABLE, false));
    }
    public String modelName() { return modelName; }
    public boolean isSpeaker() { return speaker; }
    public boolean isStand() { return "pink_microphone_stand".equals(modelName); }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, MusicPlayerBlock.CYCLE_DISABLE);
    }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
        return isStand() && (!KaraokeStandPlacement.canReserveTop(context.getLevel(), context.getClickedPos())
                || !KaraokeStandPlacement.hasSpace(context.getLevel(), context.getClickedPos(), standShape(state, false))) ? null : state;
    }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new KaraokeBlockEntity(pos, state); }
    @Override public RenderShape getRenderShape(BlockState state) { return RenderShape.ENTITYBLOCK_ANIMATED; }
    @Override public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        boolean side = state.getValue(FACING).getAxis() == Direction.Axis.X;
        if (isStand()) return standShape(state, level.getBlockEntity(pos) instanceof KaraokeBlockEntity device && device.hasMicrophone());
        if (speaker) return side ? box(3.5,0,2,12.5,15.7,14) : box(2,0,3.5,14,15.7,12.5);
        if (modelName.equals("pink_handheld_microphone")) return box(5.8,0,5.8,10.2,14.2,10.2);
        return side ? box(8-1.807,0,8-2.585,8+1.807,11.7375,8+2.585)
                : box(8-2.585,0,8-1.807,8+2.585,11.7375,8+1.807);
    }

    public static VoxelShape standShape(BlockState state, boolean loaded) {
        return standShape(state.getValue(FACING), loaded);
    }
    public static VoxelShape standShape(Direction facing, boolean loaded) {
        int turns = switch (facing) { case EAST -> 1; case SOUTH -> 2; case WEST -> 3; default -> 0; };
        return STAND_SHAPES[loaded ? 1 : 0][turns];
    }
    private static VoxelShape[][] createStandShapes() {
        VoxelShape[][] shapes = new VoxelShape[2][4];
        for (int loaded = 0; loaded < 2; loaded++) {
        VoxelShape shape = Shapes.or(box(2.7,0,4,13.3,1.82,12), box(6.75,1.62,8.1,9.25,17.7,10.4),
                box(6.67,17.1,6.18,9.33,19.35,9.97));
        if (loaded == 1) shape = Shapes.or(shape, box(7.1,16.168,6.22,8.9,25.8,8.3), box(5.81,25.25,5.11,10.19,30.12,9.49));
        for (int i = 0; i < 4; i++) {
            shapes[loaded][i] = shape.optimize();
            VoxelShape rotated = Shapes.empty();
            for (var part : shape.toAabbs()) rotated = Shapes.or(rotated, Shapes.box(1-part.maxZ,part.minY,part.minX,1-part.minZ,part.maxY,part.maxX));
            shape = rotated;
        }
        }
        return shapes;
    }
    @Override public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                           InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof KaraokeBlockEntity device) || player.isSpectator()) return InteractionResult.PASS;
        if (isStand()) {
            ItemStack held = player.getItemInHand(hand);
            if (!device.hasMicrophone()) {
                if (!player.isShiftKeyDown() && KaraokeMicrophoneData.isHandheld(held)) {
                    if (!KaraokeStandPlacement.canReserveTop(level, pos) || !KaraokeStandPlacement.hasSpace(level, pos, standShape(state, true))) {
                        if (!level.isClientSide) player.displayClientMessage(net.minecraft.network.chat.Component.literal("上方空间不足，无法装上麦克风"), true);
                        return InteractionResult.FAIL;
                    }
                    if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) install(device, serverPlayer, hand);
                    return InteractionResult.sidedSuccess(level.isClientSide);
                }
                if (hand == InteractionHand.MAIN_HAND && !player.isShiftKeyDown() && held.isEmpty()
                        && KaraokeMicrophoneData.isHandheld(player.getOffhandItem())) return InteractionResult.PASS;
                if (hand == InteractionHand.MAIN_HAND && !level.isClientSide)
                    player.displayClientMessage(net.minecraft.network.chat.Component.literal("拿着手持麦克风，普通右键装到架子上"), true);
                return InteractionResult.sidedSuccess(level.isClientSide);
            }
            if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
            if (!player.isShiftKeyDown() && held.isEmpty()) {
                if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) removeMicrophone(device, serverPlayer);
                return InteractionResult.sidedSuccess(level.isClientSide);
            }
        } else if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            device.deviceId();
            net.minecraftforge.network.NetworkHooks.openScreen(serverPlayer,
                    new SimpleMenuProvider((window, inv, p) -> new MusicPlayerPlaylistMenu(window, inv, device),
                            asItem().getDescription()), buf -> buf.writeBlockPos(pos));
            KaraokeServer.sendState(serverPlayer, "");
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    private static void install(KaraokeBlockEntity device, ServerPlayer player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (device.hasMicrophone() || !KaraokeMicrophoneData.isHandheld(held) || player.isSpectator()
                || device.getLevel() != player.level() || !player.level().mayInteract(player, device.getBlockPos())) return;
        if (!KaraokeStandPlacement.ensureTop(player.level(), device.getBlockPos())) return;
        KaraokeServer.observeItem(player, held);
        KaraokeServer.stopItemForTransfer(player, held);
        KaraokeServer.stopDeviceForTransfer(device);
        ItemStack attached = held.copy(); attached.setCount(1);
        boolean retainsOriginal = player.getAbilities().instabuild || held.getCount() > 1;
        if (retainsOriginal) {
            attached.getOrCreateTag().putUUID(KaraokeMicrophoneItem.ID_TAG, KaraokeData.get(player.server).create());
            attached.getOrCreateTag().putUUID("MusicPlayerInstanceId", UUID.randomUUID());
        }
        device.mountMicrophone(attached);
        if (!retainsOriginal) KaraokeServer.releaseClaim(KaraokeMicrophoneItem.getId(held), held);
        if (!player.getAbilities().instabuild) held.shrink(1);
        KaraokeServer.registerDevice(device);
    }

    private static void removeMicrophone(KaraokeBlockEntity device, ServerPlayer player) {
        if (!device.hasMicrophone() || !player.getMainHandItem().isEmpty() || player.isSpectator()
                || device.getLevel() != player.level() || !player.level().mayInteract(player, device.getBlockPos())) return;
        KaraokeServer.stopDeviceForTransfer(device);
        ItemStack microphone = device.mountedMicrophone();
        if (microphone.isEmpty()) return;
        device.clearMicrophone();
        player.setItemInHand(InteractionHand.MAIN_HAND, microphone);
        KaraokeServer.observeItem(player, microphone);
    }
    @Override public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide || !(level.getBlockEntity(pos) instanceof KaraokeBlockEntity device)) return;
        KaraokeServer.stopDeviceForTransfer(device);
        if (isStand()) {
            device.clearMicrophone(); device.setStandItem(stack); device.markDirty();
            KaraokeStandPlacement.ensureTop(level, pos);
            return;
        }
        if (placer instanceof ServerPlayer player && stack.getItem() instanceof KaraokeMicrophoneItem) KaraokeServer.stopItemForTransfer(player, stack);
        CompoundTag tag = stack.getTag();
        if (tag != null && tag.contains("KaraokeBlockData", 10)) device.load(tag.getCompound("KaraokeBlockData"));
        device.setItemExtras(tag == null ? new CompoundTag() : tag);
        if (!speaker) {
            var id = KaraokeMicrophoneItem.ensureId(stack, level.getServer());
            KaraokeServer.releaseClaim(id, stack);
            device.setDeviceId(id);
        }
        if (tag != null && tag.contains("Item", 10)) {
            var cds = MusicPlayerItem.loadAllCds(stack);
            for (int i=0;i<device.getPlayerInv().getSlots();i++) device.getPlayerInv().setStackInSlot(i, cds.get(i).copy());
            device.setPlayIndex(MusicPlayerItem.getPlayIndex(stack)); device.setPlayMode(MusicPlayerItem.getPlayMode(stack));
        }
        device.setPlay(false); device.setPaused(false); device.setCurrentTime(0); device.setMicrophoneActive(false);
        KaraokeServer.registerDevice(device); device.markDirty();
    }
    public ItemStack preservedDrop(KaraokeBlockEntity device) {
        if (isStand()) {
            ItemStack stand = device.standItem();
            return stand.isEmpty() ? new ItemStack(asItem()) : stand;
        }
        ItemStack stack = new ItemStack(asItem()); stack.setTag(device.itemExtras());
        CompoundTag blockTag = device.saveWithoutMetadata();
        blockTag.remove("KaraokeItemExtras"); blockTag.putBoolean("IsPlay", false); blockTag.putBoolean("IsPaused", false);
        blockTag.putInt("CurrentTime", 0); stack.getOrCreateTag().put("KaraokeBlockData", blockTag);
        if (!speaker && device.deviceId() != null) stack.getOrCreateTag().putUUID(KaraokeMicrophoneItem.ID_TAG, device.deviceId());
        var cds = NonNullList.withSize(54, ItemStack.EMPTY);
        for(int i=0;i<cds.size();i++) cds.set(i, device.getPlayerInv().getStackInSlot(i).copy());
        int previousIndex = Math.max(0, Math.min(cds.size()-1, device.getPlayIndex()));
        int compactIndex = 0;
        if (!cds.get(previousIndex).isEmpty()) for (int i=0;i<previousIndex;i++) if(!cds.get(i).isEmpty())compactIndex++;
        MusicPlayerItem.saveAllCdsToItem(stack, cds); MusicPlayerItem.setPlayIndex(stack, compactIndex);
        MusicPlayerItem.setPlayMode(stack, device.getPlayMode()); MusicPlayerItem.setPlay(stack, false);
        MusicPlayerItem.setPaused(stack, false); MusicPlayerItem.setCurrentTime(stack, 0);
        return stack;
    }
    @Override public void onRemove(BlockState state, Level level, BlockPos pos, BlockState replacement, boolean moving) {
        if (!state.is(replacement.getBlock()) && !level.isClientSide && level.getBlockEntity(pos) instanceof KaraokeBlockEntity device) {
            KaraokeServer.stopDeviceForTransfer(device);
            if (!moving && device.claimDrops()) {
                ItemStack blockItem = preservedDrop(device);
                ItemStack microphone = isStand() ? device.mountedMicrophone() : ItemStack.EMPTY;
                if (isStand()) device.clearMicrophone();
                Block.popResource(level, pos, blockItem);
                if (!microphone.isEmpty()) Block.popResource(level, pos, microphone);
            }
        }
        if (isStand() && !state.is(replacement.getBlock()) && !level.isClientSide && !level.restoringBlockSnapshots
                && level.getBlockState(pos.above()).getBlock() instanceof KaraokeStandTopBlock) level.removeBlock(pos.above(), false);
        super.onRemove(state, level, pos, replacement, moving);
    }
    @Override @SuppressWarnings("unchecked") public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide || type != ModBlockEntities.KARAOKE_DEVICE.get() ? null :
                (world, pos, blockState, be) -> { var device = (KaraokeBlockEntity)be;
                    if (device.tickLifecycle() && (!device.isStand() || device.hasMicrophone())) MusicPlayerBlockEntity.tick(world, pos, blockState, device); };
    }
}

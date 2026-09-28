package com.mengsama.mod.mengsamanetmusic.block;

import com.mengsama.mod.mengsamanetmusic.init.ModBlockEntities;
import com.mengsama.mod.mengsamanetmusic.earbuds.EarbudSlots;
import com.mengsama.mod.mengsamanetmusic.network.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.*;
import software.bernie.geckolib.util.GeckoLibUtil;

 
public class PortableMusicPlayerBlockEntity extends MusicDeviceEntity implements GeoBlockEntity {
    private final AnimatableInstanceCache animation = GeckoLibUtil.createInstanceCache(this);
    private CompoundTag carriedData = new CompoundTag();
    private long controlsAvailableAt;
    public PortableMusicPlayerBlockEntity(BlockPos position, BlockState state) { super(ModBlockEntities.PORTABLE_MUSIC_PLAYER.get(), position, state); }
    @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return animation; }
    @Override public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "playback", 0,
                frame -> frame.setAndContinue(PlacedMusicAnimations.walkman(isPlay(), isPaused()))));
        AnimationController<PortableMusicPlayerBlockEntity> keys = new AnimationController<>(this, "buttons", 0,
                frame -> software.bernie.geckolib.core.object.PlayState.STOP);
        for (WalkmanControl key : WalkmanControl.values()) keys.triggerableAnim(key.animation,
                RawAnimation.begin().thenPlay("animation.pink_walkman." + key.animation));
        controllers.add(keys);
    }
    public void pressControl(WalkmanControl key) {
        if (!(level instanceof ServerLevel) || key == null || level.getGameTime() < controlsAvailableAt) return;
        controlsAvailableAt = level.getGameTime() + 6;
        triggerAnim("buttons", key.animation);
        if (key == WalkmanControl.PLAY_PAUSE && isPlay()) {
            setPaused(!isPaused());
            ModNetwork.sendToNearby(level, worldPosition, new PauseMusicPacketClient(blockTargetId(), isPaused(), currentRequestGeneration()));
            return;
        }
        if (key != WalkmanControl.PLAY_PAUSE) {
            stopForTransfer();
            playlist.manual(key == WalkmanControl.PREVIOUS ? -1 : 1);
        }
        playSelected();
    }
    public void retainItemData(CompoundTag data) { carriedData = data == null ? new CompoundTag() : data.copy(); setChanged(); }
    public CompoundTag retainedItemData() { return carriedData.copy(); }
    public boolean hasWiredEarbuds() { return carriedData.getCompound(EarbudSlots.KEY).getCompound("Slot0").getByte("Count") > 0; }
    public boolean hasConnectedEarbuds() { return !carriedData.getCompound(EarbudSlots.KEY).isEmpty(); }
    @Override protected boolean allowsPlayback() { return !hasConnectedEarbuds(); }
    @Override public void saveAdditional(CompoundTag data) { super.saveAdditional(data); data.put("PortableItemData", carriedData.copy()); }
    @Override public void load(CompoundTag data) { super.load(data); carriedData = data.getCompound("PortableItemData").copy(); }
    @Override public AABB getRenderBoundingBox() { return new AABB(worldPosition); }
    public static void tick(Level world, BlockPos position, BlockState state, PortableMusicPlayerBlockEntity device) { device.tickPlayback(true); }
}

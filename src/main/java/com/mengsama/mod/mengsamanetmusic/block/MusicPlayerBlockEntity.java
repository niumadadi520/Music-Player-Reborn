package com.mengsama.mod.mengsamanetmusic.block;

import com.mengsama.mod.mengsamanetmusic.init.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.util.GeckoLibUtil;

 
public class MusicPlayerBlockEntity extends MusicDeviceEntity implements GeoBlockEntity {
    private final AnimatableInstanceCache animation = GeckoLibUtil.createInstanceCache(this);
    public MusicPlayerBlockEntity(BlockPos position, BlockState state) { this(ModBlockEntities.MUSIC_PLAYER.get(), position, state); }
    protected MusicPlayerBlockEntity(BlockEntityType<?> type, BlockPos position, BlockState state) { super(type, position, state); }
    @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return animation; }
    @Override public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "playback", 0,
                frame -> frame.setAndContinue(PlacedMusicAnimations.gramophone(isPlay(), isPaused()))));
    }
    public AABB getRenderBoundingBox() { return new AABB(worldPosition).inflate(0.75, 0, 0.75).expandTowards(0, 1.2, 0); }
    public static void tick(Level world, BlockPos position, BlockState state, MusicPlayerBlockEntity device) {
        boolean repeat = !state.hasProperty(MusicPlayerBlock.CYCLE_DISABLE) || !state.getValue(MusicPlayerBlock.CYCLE_DISABLE);
        device.tickPlayback(repeat);
    }
}

package com.mengsama.mod.mengsamanetmusic.item;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;
import java.util.function.Consumer;

 
public class MusicDeviceBlockItem extends BlockItem implements GeoItem {
    private final String modelName;
    private final AnimatableInstanceCache animationCache = GeckoLibUtil.createInstanceCache(this);
    public MusicDeviceBlockItem(Block block, Properties properties, String modelName) {
        super(block, properties);
        this.modelName = modelName;
    }
    @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return animationCache; }
    @Override public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        if(!modelName.equals("pink_walkman"))return;
        controllers.add(new software.bernie.geckolib.animation.AnimationController<>(this,"handheld_playback",0,state->{
            var stack=state.getData(software.bernie.geckolib.constant.DataTickets.ITEMSTACK);
            var perspective=state.getData(software.bernie.geckolib.constant.DataTickets.ITEM_RENDER_PERSPECTIVE);
            return state.setAndContinue(software.bernie.geckolib.animation.RawAnimation.begin().thenLoop(
                    MusicPlayerItem.handheldPlaybackAnimation(stack,perspective)));
        }));
    }
    public void createClientExtensions(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new IClientItemExtensions() {
            private net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer renderer;
            @Override public net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (renderer == null) renderer = new com.mengsama.mod.mengsamanetmusic.client.renderer.AudioItemRenderer<>(modelName);
                return renderer;
            }
        });
    }
}

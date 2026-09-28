package com.mengsama.mod.mengsamanetmusic.item;

import com.mengsama.mod.mengsamanetmusic.client.renderer.PinkHeadphonesRenderer;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.function.Consumer;

 
public final class PinkHeadphonesItem extends ArmorItem implements GeoItem {
    private final String modId;
    private final AnimatableInstanceCache animationCache = GeckoLibUtil.createInstanceCache(this);

    public PinkHeadphonesItem(String modId, net.minecraft.core.Holder<ArmorMaterial> material, Properties properties) {
        super(material, Type.HELMET, properties);
        this.modId = modId;
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.animationCache;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
         
    }

    public void createClientExtensions(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new IClientItemExtensions() {
            private PinkHeadphonesRenderer headphonesRenderer;
            private net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer itemRenderer;

            @Override
            public net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (itemRenderer == null) itemRenderer = new com.mengsama.mod.mengsamanetmusic.client.renderer.AudioItemRenderer<>("pink_headphones");
                return itemRenderer;
            }

            @Override
            public HumanoidModel<?> getHumanoidArmorModel(LivingEntity wearer, ItemStack stack,
                                                          EquipmentSlot slot, HumanoidModel<?> original) {
                if (this.headphonesRenderer == null) {
                    this.headphonesRenderer = new PinkHeadphonesRenderer(PinkHeadphonesItem.this.modId);
                }
                this.headphonesRenderer.prepForRender(wearer, stack, slot, original);
                return this.headphonesRenderer;
            }
        });
    }
}

package com.mengsama.mod.mengsamanetmusic.mixin;

import com.mengsama.mod.mengsamanetmusic.compat.backpack.BackpackClient;
import com.mengsama.mod.mengsamanetmusic.compat.backpack.charm.BackpackCharmRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.p3pp3rf1y.sophisticatedbackpacks.api.CapabilityBackpackWrapper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.Group;
import org.spongepowered.asm.mixin.injection.Coerce;

@Pseudo
@Mixin(targets = "net.p3pp3rf1y.sophisticatedbackpacks.client.render.BackpackLayerRenderer", remap = false)
public abstract class BackpackCharmLayerMixin {
    @Group(name = "mengsama_backpack_render", min = 1, max = 1)
    @Inject(method = "renderBackpack(Lnet/minecraft/client/model/EntityModel;Lnet/minecraft/world/entity/LivingEntity;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/item/ItemStack;Z)V", at = @At("TAIL"), remap = false, require = 0)
    private static void mengsama$charm(EntityModel<?> model, LivingEntity entity, PoseStack pose,
            MultiBufferSource buffers, int light, ItemStack backpack, boolean armor, CallbackInfo ci) {
        backpack.getCapability(CapabilityBackpackWrapper.getCapabilityInstance()).ifPresent(wrapper -> {
            if (BackpackClient.hasCharm(wrapper.getRenderInfo()))
                BackpackCharmRenderer.render(entity, pose, buffers, light, wrapper.getRenderInfo().getBatteryRenderInfo().isPresent(), BackpackClient.charmDevice(wrapper.getRenderInfo()));
        });
    }

    @Group(name = "mengsama_backpack_render", min = 1, max = 1)
    @Inject(method = "renderBackpack(Lnet/minecraft/client/model/EntityModel;Lnet/minecraft/world/entity/LivingEntity;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/item/ItemStack;ZLnet/p3pp3rf1y/sophisticatedbackpacks/client/render/IBackpackModel;)V", at = @At("TAIL"), remap = false, require = 0)
    private static void mengsama$legacyCharm(EntityModel<?> model, LivingEntity entity, PoseStack pose,
            MultiBufferSource buffers, int light, ItemStack backpack, boolean armor, @Coerce Object oldModel, CallbackInfo ci) {
         
        pose.pushPose();
        pose.translate(0, 1, 0);
        pose.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(180));
        mengsama$charm(model, entity, pose, buffers, light, backpack, armor, ci);
        pose.popPose();
    }
}

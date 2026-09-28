package com.mengsama.mod.mengsamanetmusic.client.renderer;

import com.mengsama.mod.mengsamanetmusic.init.ModItems;
import com.mengsama.mod.mengsamanetmusic.item.PinkHeadphonesItem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.client.CuriosRendererRegistry;
import top.theillusivec4.curios.api.client.ICurioRenderer;

public final class CuriosHeadphonesRenderer implements ICurioRenderer {
    private final PinkHeadphonesRenderer renderer = new PinkHeadphonesRenderer("mengsamanetmusic");
    public static void register() { CuriosRendererRegistry.register(ModItems.PINK_HEADPHONES.get(), CuriosHeadphonesRenderer::new); }
    @Override public <T extends LivingEntity, M extends EntityModel<T>> void render(ItemStack stack, SlotContext context,
            PoseStack pose, RenderLayerParent<T, M> parent, MultiBufferSource buffers, int light,
            float limbSwing, float limbAmount, float partialTick, float age, float yaw, float pitch) {
        if (!(parent.getModel() instanceof HumanoidModel<?> humanoid)
                || context.entity().getItemBySlot(EquipmentSlot.HEAD).getItem() instanceof PinkHeadphonesItem) return;
        renderer.prepForRender(context.entity(), stack, EquipmentSlot.HEAD, humanoid);
        var consumer = buffers.getBuffer(renderer.renderType(renderer.getTextureLocation((PinkHeadphonesItem) stack.getItem())));
        renderer.renderToBuffer(pose, consumer, light, OverlayTexture.NO_OVERLAY, 1, 1, 1, 1);
    }
}

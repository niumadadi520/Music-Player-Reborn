package com.mengsama.mod.mengsamanetmusic.client.renderer;

import com.mengsama.mod.mengsamanetmusic.client.model.AudioItemModel;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemDisplayContext;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.core.animatable.GeoAnimatable;
import software.bernie.geckolib.renderer.GeoItemRenderer;

public final class AudioItemRenderer<T extends Item & GeoAnimatable> extends GeoItemRenderer<T> {
    private final double centerY;
    private ItemStack renderedStack = ItemStack.EMPTY;
    private ItemDisplayContext renderedContext;
    public AudioItemRenderer(String name) {
        super(new AudioItemModel<>(name));
        centerY = switch (name) {
            case "pink_headphones" -> 30.58 / 16;
            case "rose_gramophone" -> 13.7 / 16;
            case "pink_microphone" -> 5.86875 / 16;
            case "pink_microphone_stand" -> 9.675 / 16;
            case "pink_handheld_microphone" -> 7.06 / 16;
            case "pink_speaker" -> 7.825 / 16;
            default -> 6.0 / 16;
        };
        useAlternateGuiLighting();
    }
    @Override public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack pose,
                                       MultiBufferSource buffers, int light, int overlay) {
        renderedStack=stack; renderedContext=context;
        ((AudioItemModel<T>)getGeoModel()).setWiredDisplay(stack.getItem() instanceof com.mengsama.mod.mengsamanetmusic.item.MusicPlayerItem && com.mengsama.mod.mengsamanetmusic.earbuds.EarbudSlots.wired(stack) && (context==ItemDisplayContext.GUI || context==ItemDisplayContext.GROUND));
        ((AudioItemModel<T>)getGeoModel()).setCompact(context == ItemDisplayContext.GUI || context == ItemDisplayContext.GROUND);
        super.renderByItem(stack, context, pose, buffers, light, overlay);
    }
    @Override public long getInstanceId(T item) {
        if(renderedStack.getItem() instanceof com.mengsama.mod.mengsamanetmusic.item.MusicPlayerItem){
            var id=com.mengsama.mod.mengsamanetmusic.item.MusicPlayerItem.getInstanceId(renderedStack);
            if(id!=null)return id.getMostSignificantBits()^id.getLeastSignificantBits();
        }
        return super.getInstanceId(item);
    }
    @Override public void preRender(PoseStack pose, T item, BakedGeoModel model, MultiBufferSource buffers,
                                    VertexConsumer buffer, boolean reRender, float partialTick, int light, int overlay,
                                    float red, float green, float blue, float alpha) {
        super.preRender(pose,item,model,buffers,buffer,reRender,partialTick,light,overlay,red,green,blue,alpha);
        if (!reRender) {
            pose.translate(0, -centerY, 0);
            if (renderedStack.getItem() instanceof com.mengsama.mod.mengsamanetmusic.item.MusicPlayerItem
                    && (renderedContext==ItemDisplayContext.THIRD_PERSON_LEFT_HAND || renderedContext==ItemDisplayContext.THIRD_PERSON_RIGHT_HAND))
                com.mengsama.mod.mengsamanetmusic.earbuds.client.EarbudRender.capturePlug(com.mengsama.mod.mengsamanetmusic.item.MusicPlayerItem.getInstanceId(renderedStack),pose);
        }
    }
}

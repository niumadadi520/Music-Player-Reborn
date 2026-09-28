package com.mengsama.mod.mengsamanetmusic.earbuds.client;
import com.mengsama.mod.mengsamanetmusic.platform.ItemData;
import com.mengsama.mod.mengsamanetmusic.earbuds.*;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.renderer.GeoItemRenderer;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.constant.DataTickets;
public final class EarbudItemRenderer extends GeoItemRenderer<EarbudItem> {
    public EarbudItemRenderer(EarbudItem item) { super(new Model()); useAlternateGuiLighting(); }
    private static class Model extends GeoModel<EarbudItem> {
        @Override public ResourceLocation getModelResource(EarbudItem e) { return resource("geo/"+e.model+".geo.json"); }
        @Override public ResourceLocation getTextureResource(EarbudItem e) { return resource(e.kind==3?"textures/item/pink_bluetooth_case.png":"textures/armor/pink_earbuds.png"); }
        @Override public ResourceLocation getAnimationResource(EarbudItem e) { return resource("animations/"+(e.kind==3?e.model:"pink_earbuds")+".animation.json"); }
        @Override public void setCustomAnimations(EarbudItem e,long id,AnimationState<EarbudItem> state) {
            if(e.kind!=3)return;
            var stack=state.getData(DataTickets.ITEMSTACK);if(stack==null)return;
            boolean initialized=ItemData.has(stack)&&ItemData.nullable(stack).getBoolean("EarbudCaseInitialized");
            getBone("earbud_left_in_case").ifPresent(b->b.setHidden(initialized&&EarbudSlots.get(stack,1).isEmpty()));
            getBone("earbud_right_in_case").ifPresent(b->b.setHidden(initialized&&EarbudSlots.get(stack,2).isEmpty()));
        }
    }
    static ResourceLocation resource(String path) { return ResourceLocation.fromNamespaceAndPath("mengsamanetmusic", path); }
    @Override public void preRender(PoseStack pose,EarbudItem item,BakedGeoModel model,MultiBufferSource buffers,VertexConsumer buffer,boolean rerender,float partial,int light,int overlay,int color) {
        super.preRender(pose,item,model,buffers,buffer,rerender,partial,light,overlay,color);
        if(!rerender && item.kind!=3) pose.translate(item.kind==1?3.72/16:item.kind==2?-3.72/16:0,-27.9/16,0);
    }
}

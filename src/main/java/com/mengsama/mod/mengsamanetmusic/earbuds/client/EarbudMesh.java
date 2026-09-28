package com.mengsama.mod.mengsamanetmusic.earbuds.client;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoObjectRenderer;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import java.util.*;
 
final class EarbudMesh implements GeoAnimatable {
    private static final Map<String,EarbudMesh> CACHE=new HashMap<>();
    private final String name;private final AnimatableInstanceCache cache=GeckoLibUtil.createInstanceCache(this);
    private final GeoObjectRenderer<EarbudMesh> renderer;
    private EarbudMesh(String name) {
        this.name=name;
        renderer=new GeoObjectRenderer<EarbudMesh>(new GeoModel<EarbudMesh>() {
            @Override public ResourceLocation getModelResource(EarbudMesh m){return EarbudItemRenderer.resource("geo/"+m.name+".geo.json");}
            @Override public ResourceLocation getTextureResource(EarbudMesh m){return EarbudItemRenderer.resource(m.name.contains("transfer_piece")?"textures/entity/pink_transfer_pieces.png":"textures/armor/pink_earbuds.png");}
            @Override public ResourceLocation getAnimationResource(EarbudMesh m){return EarbudItemRenderer.resource("animations/"+m.name+".animation.json");}
        }) {
            @Override public void preRender(PoseStack p,EarbudMesh m,BakedGeoModel model,MultiBufferSource b,VertexConsumer v,boolean re,float pt,int light,int overlay,int color) {   }
        };
    }
    static void render(String name,PoseStack pose,MultiBufferSource buffers,int light) {
        EarbudMesh m=CACHE.computeIfAbsent(name,EarbudMesh::new);RenderType type=RenderType.entityCutoutNoCull(m.renderer.getTextureLocation(m));
        m.renderer.render(pose,m,buffers,type,buffers.getBuffer(type),light,0);
    }
    @Override public void registerControllers(AnimatableManager.ControllerRegistrar r) {}
    @Override public AnimatableInstanceCache getAnimatableInstanceCache(){return cache;}
    @Override public double getTick(Object object){var mc=Minecraft.getInstance();return mc.level==null?0:mc.level.getGameTime();}
}

package com.mengsama.mod.mengsamanetmusic.earbuds.client;

import com.mengsama.mod.mengsamanetmusic.earbuds.*;
import com.mengsama.mod.mengsamanetmusic.earbuds.client.handoff.HandoffClientAdapter;
import com.mengsama.mod.mengsamanetmusic.earbuds.handoff.HandoffMotion;
import com.mengsama.mod.mengsamanetmusic.earbuds.handoff.HandoffState;
import com.mengsama.mod.mengsamanetmusic.earbuds.client.cable.*;
import com.mengsama.mod.mengsamanetmusic.earbuds.client.cable.CableCurve.Point;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.*;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import java.util.*;

@Mod.EventBusSubscriber(modid="mengsamanetmusic",value=Dist.CLIENT)
public final class EarbudRender {
    private record Frame(Matrix4f head,Matrix4f body,int light) {}
    private static final Map<UUID,Frame> HEADS=new HashMap<>();
    private static final CableAttachments PLUGS = new CableAttachments();
    private static boolean capturing;
    public static void clear(){HEADS.clear();PLUGS.clear();capturing=false;}
    public static void capturePlug(UUID device,PoseStack pose) {
        if(capturing)PLUGS.capture(device,CableAttachments.Kind.HAND,WiredCableRenderer.capture(pose,-3.29/16,10.915/16,.95/16));
    }
    public static void captureBackpackPlug(UUID device,PoseStack pose,double chainPitch,double chainRoll,double pitch,double yaw,double roll) {
        if(!capturing)return;pose.pushPose();
        pose.mulPose(new Quaternionf().rotationZYX((float)Math.toRadians(chainRoll),0,(float)Math.toRadians(chainPitch)));
        pose.translate(0,-2,0);pose.mulPose(new Quaternionf().rotationZYX((float)Math.toRadians(roll),(float)Math.toRadians(yaw),(float)Math.toRadians(pitch)));pose.translate(0,2,0);
        PLUGS.capture(device,CableAttachments.Kind.BACKPACK,WiredCableRenderer.capture(pose,-.8225,-2.235,.2375));pose.popPose();
    }
    @Mod.EventBusSubscriber(modid="mengsamanetmusic",value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.MOD)
    public static final class Layers {
        @SubscribeEvent public static void add(EntityRenderersEvent.AddLayers event) {
            for(String skin:event.getSkins()) {PlayerRenderer renderer=event.getSkin(skin);if(renderer!=null)renderer.addLayer(new CaptureLayer(renderer));}
        }
    }
    private static final class CaptureLayer extends RenderLayer<AbstractClientPlayer,PlayerModel<AbstractClientPlayer>> {
        CaptureLayer(PlayerRenderer renderer){super(renderer);}
        @Override public void render(PoseStack pose,MultiBufferSource buffers,int light,AbstractClientPlayer player,float swing,float amount,float partial,float age,float yaw,float pitch) {
            if(!capturing)return;pose.pushPose();getParentModel().head.translateAndRotate(pose);Matrix4f head=new Matrix4f(pose.last().pose());pose.popPose();
            pose.pushPose();getParentModel().body.translateAndRotate(pose);Matrix4f body=new Matrix4f(pose.last().pose());pose.popPose();
            HEADS.put(player.getUUID(),new Frame(head,body,light));
        }
    }
    @SubscribeEvent public static void stage(RenderLevelStageEvent event) {
        if(event.getStage()==RenderLevelStageEvent.Stage.AFTER_SKY){clear();capturing=true;return;}
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_ENTITIES)return;
        var mc=Minecraft.getInstance();if(mc.level==null||mc.player==null){capturing=false;return;}
        float partial=event.getPartialTick();double tick=EarbudClient.tick(partial);MultiBufferSource.BufferSource buffers=mc.renderBuffers().bufferSource();
        Point gravity=WiredCableRenderer.captureDirection(event.getPoseStack(),0,-1,0);
        for(CompoundTag state:EarbudClient.DEVICES.values()) {
            Player owner=mc.level.getPlayerByUUID(state.getUUID("Owner"));if(owner==null||!owner.isAlive()||owner.isInvisibleTo(mc.player))continue;
            Player guest=state.hasUUID("Guest")?mc.level.getPlayerByUUID(state.getUUID("Guest")):null;
            Frame a=frame(owner,event),b=guest==null?null:frame(guest,event);
            boolean wired=state.getBoolean("Wired");int mask=state.getInt("Mask"),side=state.getInt("Side");
            double seconds=(tick-state.getLong("Start"))/20;boolean sharing=guest!=null,committed=state.getBoolean("Committed");
            Point movingTip=null;boolean moving=sharing&&!committed&&seconds>=HandoffMotion.CONTACT;
            if(moving) {
                var chosen=side==0?HandoffMotion.Side.LEFT:HandoffMotion.Side.RIGHT;
                var session=new HandoffState.Session(state.getUUID("Session"),owner.getUUID(),guest.getUUID(),chosen,wired,state.getLong("Start"),HandoffState.Status.ACTIVE);
                var frame=HandoffClientAdapter.sample(HandoffClientAdapter.capture(a.head),HandoffClientAdapter.capture(b.head),session,(long)tick,(float)(tick-Math.floor(tick)),new HandoffMotion.Vec(-gravity.x(),-gravity.y(),-gravity.z()));
                if(frame.drawMovingEar()) {
                    HandoffClientAdapter.renderMovingGeckoPiece(frame,chosen,p->EarbudMesh.render("pink_"+(wired?"wired":"bluetooth")+"_transfer_piece_"+(side==0?"left":"right"),p,buffers,a.light));
                    if(frame.cableTip()!=null)movingTip=new Point(frame.cableTip().x(),frame.cableTip().y(),frame.cableTip().z());
                } else {
                     
                    float u=(float)Mth.clamp((seconds-.55)/2.10,0,1);u=u*u*(3-2*u);
                    Vector3f translation=a.head.getTranslation(new Vector3f()).lerp(b.head.getTranslation(new Vector3f()),u);
                    translation.add((float)(gravity.x()*.25*4*u*(1-u)),(float)(gravity.y()*.25*4*u*(1-u)),(float)(gravity.z()*.25*4*u*(1-u)));
                    Quaternionf rotation=new Quaternionf().setFromUnnormalized(a.head).normalize().slerp(new Quaternionf().setFromUnnormalized(b.head).normalize(),u);
                    Vector3f scale=a.head.getScale(new Vector3f()).lerp(b.head.getScale(new Vector3f()),u);
                    Matrix4f transform=new Matrix4f().translationRotateScale(translation,rotation,scale);
                    movingTip=point(transform,chosen.wireExitLocal());
                    wear(transform,wired,side==0?1:2,buffers,a.light);
                }
            }
            int ownerMask=sharing&&(committed||moving)?mask&~(1<<side):mask;
            if(!(owner==mc.player&&mc.options.getCameraType().isFirstPerson()))wear(a.head,wired,ownerMask,buffers,a.light);
            if(sharing&&committed&&!(guest==mc.player&&mc.options.getCameraType().isFirstPerson()))wear(b.head,wired,1<<side,buffers,b.light);
            if(wired && CableAttachments.visible(mc.options.getCameraType().isFirstPerson(),
                    mc.getCameraEntity()==null?null:mc.getCameraEntity().getUUID(), owner.getUUID(), guest==null?null:guest.getUUID())) {
                Point left=(mask&1)!=0?point(a.head,HandoffMotion.Side.LEFT.wireExitLocal()):null;
                Point right=(mask&2)!=0?point(a.head,HandoffMotion.Side.RIGHT.wireExitLocal()):null;
                if(sharing&&(committed||moving)) {Point endpoint=committed?point(b.head,(side==0?HandoffMotion.Side.LEFT:HandoffMotion.Side.RIGHT).wireExitLocal()):movingTip;if(side==0)left=endpoint;else right=endpoint;}
                var socket=PLUGS.resolve(state.getUUID("Device"));
                boolean backpack=socket!=null ? socket.kind()==CableAttachments.Kind.BACKPACK : "backpack".equals(state.getString("Attachment"));
                Point plug=socket==null ? body(a,backpack?-.30:-.35,.65,backpack?.84:-.12) : socket.point();
                Point splitter=body(a,backpack?-.30:0,.18,backpack?.84:-.29);
                boolean warning=sharing&&EarbudSlots.nearLimit(true,owner.distanceToSqr(guest));
                List<WiredCableRenderer.Segment> segments=new ArrayList<>(8);
                if(left!=null) {
                    if(backpack&&!(sharing&&side==0)) {Point guide=body(a,.40,-.06,.84);segments.add(new WiredCableRenderer.Segment(left,guide,.02));segments.add(new WiredCableRenderer.Segment(guide,splitter,.025));}
                    else segments.add(new WiredCableRenderer.Segment(left,splitter,warning?.018:.08));
                }
                if(right!=null) {
                    if(backpack&&!(sharing&&side==1)) {Point guide=body(a,-.40,-.06,.84);segments.add(new WiredCableRenderer.Segment(right,guide,.02));segments.add(new WiredCableRenderer.Segment(guide,splitter,.025));}
                    else segments.add(new WiredCableRenderer.Segment(right,splitter,warning?.018:.08));
                }
                segments.add(new WiredCableRenderer.Segment(splitter,plug,.055));
                WiredCableRenderer.renderSegments(new PoseStack(),buffers,segments,gravity,tick,owner.getDeltaMovement().horizontalDistance()*5,warning?LightTexture.FULL_BRIGHT:a.light,warning);
            }
        }
        capturing=false;
    }
    private static Point body(Frame frame,double x,double y,double z){Vector3f p=frame.body.transformPosition(new Vector3f((float)x,(float)y,(float)z));return new Point(p.x,p.y,p.z);}
    private static Point point(Matrix4f matrix,HandoffMotion.Vec local){Vector3f p=matrix.transformPosition(new Vector3f((float)local.x(),(float)local.y(),(float)local.z()));return new Point(p.x,p.y,p.z);}
    private static void wear(Matrix4f head,boolean wired,int mask,MultiBufferSource buffers,int light) {
        if(mask==0)return;PoseStack pose=new PoseStack();pose.mulPoseMatrix(head);pose.last().normal().set(head).invert().transpose();pose.scale(-1,-1,1);pose.translate(0,-1.5,0);
        EarbudMesh.render("pink_"+(wired?"wired":"bluetooth")+"_earbuds_"+(mask==3?"both":mask==1?"left":"right"),pose,buffers,light);
    }
    private static Frame frame(Player player,RenderLevelStageEvent event) {
        Frame cached=HEADS.get(player.getUUID());if(cached!=null)return cached;
        var camera=event.getCamera().getPosition();float partial=event.getPartialTick();PoseStack pose=new PoseStack();pose.mulPoseMatrix(event.getPoseStack().last().pose());
        pose.translate(Mth.lerp(partial,player.xo,player.getX())-camera.x,Mth.lerp(partial,player.yo,player.getY())-camera.y+1.50-(player.isCrouching()?.125:0),Mth.lerp(partial,player.zo,player.getZ())-camera.z);
        pose.mulPose(Axis.YP.rotationDegrees(180-Mth.rotLerp(partial,player.yBodyRotO,player.yBodyRot)));pose.scale(-1,-1,1);Matrix4f body=new Matrix4f(pose.last().pose());
        pose.mulPose(Axis.YP.rotationDegrees(Mth.rotLerp(partial,player.yHeadRotO,player.yHeadRot)-Mth.rotLerp(partial,player.yBodyRotO,player.yBodyRot)));pose.mulPose(Axis.XP.rotationDegrees(Mth.lerp(partial,player.xRotO,player.getXRot())));
        return new Frame(new Matrix4f(pose.last().pose()),body,LevelRenderer.getLightColor(player.level(),player.blockPosition()));
    }
}

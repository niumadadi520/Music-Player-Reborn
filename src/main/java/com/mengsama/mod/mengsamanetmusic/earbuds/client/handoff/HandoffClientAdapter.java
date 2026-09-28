package com.mengsama.mod.mengsamanetmusic.earbuds.client.handoff;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mengsama.mod.mengsamanetmusic.earbuds.handoff.HandoffMotion;
import com.mengsama.mod.mengsamanetmusic.earbuds.handoff.HandoffMotion.HeadPose;
import com.mengsama.mod.mengsamanetmusic.earbuds.handoff.HandoffMotion.Sample;
import com.mengsama.mod.mengsamanetmusic.earbuds.handoff.HandoffMotion.Side;
import com.mengsama.mod.mengsamanetmusic.earbuds.handoff.HandoffMotion.Vec;
import com.mengsama.mod.mengsamanetmusic.earbuds.handoff.HandoffState;
import net.minecraft.client.model.geom.ModelPart;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

 
public final class HandoffClientAdapter {
    private HandoffClientAdapter() {}
    public record Captured(HeadPose head, Quaternionf rotation) {}
    public record DrawFrame(Sample motion, Matrix4f modelPose, Vec cableTip,
                            boolean suppressSourceEar, boolean suppressReceiverEar,
                            boolean drawMovingEar, String cancellationReason) {}

     




    public static Captured capture(PoseStack playerModelPose,ModelPart head) {
        playerModelPose.pushPose();
        try {
            head.translateAndRotate(playerModelPose);
            return capture(new Matrix4f(playerModelPose.last().pose()));
        } finally { playerModelPose.popPose(); }
    }

    public static Captured capture(Matrix4f headLocalToCommon) {
        Matrix4f m=new Matrix4f(headLocalToCommon);
        Vector3f sx=m.transformDirection(new Vector3f(1,0,0));
        Vector3f sy=m.transformDirection(new Vector3f(0,1,0));
        Vector3f sz=m.transformDirection(new Vector3f(0,0,1));
        float scale=sx.length();
        if (Math.abs(scale-sy.length())>1e-4 || Math.abs(scale-sz.length())>1e-4 || scale<1e-6)
            return null;  
        HeadPose pose=new HeadPose(vec(m.transformPosition(new Vector3f())),vec(sx.div(scale)),
                vec(sy.div(scale)),vec(sz.div(scale)),scale);
        return pose.valid()?new Captured(pose,new Quaternionf().setFromUnnormalized(m).normalize()):null;
    }

     
    public static final class SameFrameHeads {
        private final Map<UUID,Captured> heads=new HashMap<>();
        private long frameId=Long.MIN_VALUE;
        public void beginFrame(long value) { if (value!=frameId) { heads.clear(); frameId=value; } }
        public void put(long value,UUID player,PoseStack posedPlayer,ModelPart head) {
            if (value!=frameId) throw new IllegalStateException("beginFrame must run first");
            Captured frame=capture(posedPlayer,head);
            if (frame!=null) heads.put(player,frame); else heads.remove(player);
        }
        public DrawFrame sample(long value,HandoffState.Session session,long serverTick,float partialTick,Vec commonUp) {
            if (value!=frameId) return absent("Stale frame ID");
            Captured a=heads.get(session.source()),b=heads.get(session.receiver());
            if (a==null || b==null) return absent("Both posed heads must be captured in this render frame");
            return HandoffClientAdapter.sample(a,b,session,serverTick,partialTick,commonUp);
        }
    }

     
    public static final class PlaybackGuard {
        private UUID sessionId;
        private Vec previousSeparation;
        private double previousSeconds;
        private String rejection;
        public DrawFrame sample(Captured a,Captured b,HandoffState.Session session,
                                long serverTick,float partialTick,Vec commonUp) {
            if (!session.id().equals(sessionId)) {
                sessionId=session.id(); previousSeparation=null; rejection=null; previousSeconds=0;
            }
            double seconds=session.seconds(serverTick,partialTick);
            if (a==null || b==null) rejection="Missing a current posed head";
            if (a!=null && b!=null) {
                Vec separation=b.head.center().subtract(a.head.center());
                if (previousSeparation!=null && separation.subtract(previousSeparation).length()>.40)
                    rejection="Sudden relative player displacement; cancel on server";
                if (seconds+.05<previousSeconds) rejection="Server animation clock moved backwards";
                previousSeparation=separation; previousSeconds=seconds;
            }
            if (rejection!=null) {
                boolean committed=session.status()==HandoffState.Status.COMMITTED;
                return new DrawFrame(null,null,null,committed,false,false,rejection);
            }
            DrawFrame result=HandoffClientAdapter.sample(a,b,session,serverTick,partialTick,commonUp);
            if (!result.cancellationReason().isEmpty()) rejection=result.cancellationReason();
            return result;
        }
    }

    public static DrawFrame sample(Captured a,Captured b,HandoffState.Session session,
                                   long serverTick,float partialTick,Vec commonUp) {
        if (a==null || b==null) return absent("Missing current head pose");
        double seconds=session.seconds(serverTick,partialTick);
        if (session.status()==HandoffState.Status.CANCELLED)
            return new DrawFrame(HandoffMotion.cancelled(a.head,session.side(),"Server cancelled transfer"),
                    null,null,false,false,false,"");
        Sample motion=HandoffMotion.sample(a.head,b.head,session.side(),seconds,commonUp);
        if (!motion.valid()) {
             
            boolean committed=session.status()==HandoffState.Status.COMMITTED;
            return new DrawFrame(motion,null,null,committed,false,false,motion.reason());
        }
        Quaternionf rotation=new Quaternionf(a.rotation).slerp(b.rotation,(float)motion.orientationBlend());
        float scale=(float)(a.head.scale()*(1-motion.orientationBlend())+b.head.scale()*motion.orientationBlend());
         
         
        Vec gripOffset=vec(rotation.transform(new Vector3f(session.side().vanillaXSign*.19f,0,0)));
        Vec grip=motion.earPosition().add(gripOffset);
        if (a.head.intersects(grip,.12) || b.head.intersects(grip,.12)) {
            boolean committed=session.status()==HandoffState.Status.COMMITTED;
            return new DrawFrame(null,null,null,committed,false,false,"Actual quaternion grip intersects a head");
        }
        motion=new Sample(motion.valid(),motion.reason(),motion.phase(),motion.earPosition(),grip,
                motion.orientationBlend(),motion.visibility(),motion.releaseReached(),motion.finished(),motion.lowArcDepth());
        Vec anchor=session.side().anchorLocal();
        Matrix4f matrix=new Matrix4f().translation((float)motion.earPosition().x(),
                (float)motion.earPosition().y(),(float)motion.earPosition().z())
                .rotate(rotation).scale(scale).translate((float)-anchor.x(),(float)-anchor.y(),(float)-anchor.z());
        Vec tip=vec(matrix.transformPosition(toJoml(session.side().wireExitLocal())));
         
         
        boolean active=session.status()==HandoffState.Status.ACTIVE;
        boolean started=seconds>=HandoffMotion.CONTACT;
        boolean moving=active && started;
        return new DrawFrame(motion,matrix,session.wired()?tip:null,
                active?started:true,active && started,moving,"");
    }

     




    public static void renderMoving(DrawFrame frame,EarMeshRenderer renderer) {
        if (frame==null || !frame.drawMovingEar || frame.modelPose==null) return;
        PoseStack pose=new PoseStack();
        pose.mulPose(frame.modelPose);
         
        pose.last().normal().set(frame.modelPose).invert().transpose();
        renderer.render(pose);
    }

     
    public static Matrix4f localVanillaPiecePose(DrawFrame frame,Side side) {
        if (frame.modelPose==null) return null;
        Vec a=side.anchorLocal();
        return new Matrix4f(frame.modelPose).translate((float)a.x(),(float)a.y(),(float)a.z());
    }

     




    public static Matrix4f localGeckoPiecePose(DrawFrame frame,Side side) {
        Matrix4f matrix=localVanillaPiecePose(frame,side);
        return matrix==null?null:matrix.scale(-1,-1,1);
    }

    public static void renderMovingGeckoPiece(DrawFrame frame,Side side,EarMeshRenderer renderer) {
        if (frame==null || !frame.drawMovingEar) return;
        Matrix4f matrix=localGeckoPiecePose(frame,side);
        if (matrix==null) return;
        PoseStack pose=new PoseStack();
        pose.mulPose(matrix);
        pose.last().normal().set(matrix).invert().transpose();
        renderer.render(pose);
    }
    @FunctionalInterface public interface EarMeshRenderer { void render(PoseStack posedEarMesh); }
    private static DrawFrame absent(String why) { return new DrawFrame(null,null,null,false,false,false,why); }
    private static Vec vec(Vector3f p) { return new Vec(p.x,p.y,p.z); }
    private static Vector3f toJoml(Vec p) { return new Vector3f((float)p.x(),(float)p.y(),(float)p.z()); }
}

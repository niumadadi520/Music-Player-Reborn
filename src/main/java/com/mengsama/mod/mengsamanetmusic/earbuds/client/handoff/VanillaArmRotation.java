package com.mengsama.mod.mengsamanetmusic.earbuds.client.handoff;

import com.mengsama.mod.mengsamanetmusic.earbuds.handoff.HandoffMotion;
import com.mengsama.mod.mengsamanetmusic.earbuds.handoff.HandoffMotion.Side;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;

 
public final class VanillaArmRotation {
    private VanillaArmRotation() {}
     
     
    private static final double[][] NATIVE_LEFT_KEYS = {
            {0.0, 0.0, 0.0, 0.0},
            {0.2, 60.0, 0.0, 0.0},
            {0.55, 160.0, 0.0, 0.0},
            {0.95, 145.0, 0.0, 0.0},
            {1.3, 65.0, 0.0, 0.0},
            {1.58, 75.0, 0.0, 0.0},
            {1.8, 90.0, 0.0, 0.0},
            {2.15, 120.0, 0.0, 15.0},
            {2.65, 120.0, 0.0, 15.0},
            {2.86, 110.0, 0.0, 10.0},
            {3.1, 65.0, 0.0, 0.0},
            {3.4, 0.0, 0.0, 0.0},
            {3.6, 0.0, 0.0, 0.0}
    };
    public record Angles(float xRot,float yRot,float zRot) {}

    public static Angles standardArmRotation(Side side,double seconds) {
        if (!Double.isFinite(seconds)) return new Angles(0,0,0);
        double[] first=NATIVE_LEFT_KEYS[0],last=NATIVE_LEFT_KEYS[NATIVE_LEFT_KEYS.length-1];
        double x=first[1],y=first[2],z=first[3];
        if (seconds>=last[0]) { x=last[1]; y=last[2]; z=last[3]; }
        else if (seconds>first[0]) {
            for (int i=1;i<NATIVE_LEFT_KEYS.length;i++) {
                double[] a=NATIVE_LEFT_KEYS[i-1],b=NATIVE_LEFT_KEYS[i];
                if (seconds<=b[0]) {
                    double u=HandoffMotion.smooth((seconds-a[0])/(b[0]-a[0]));
                    x=a[1]+(b[1]-a[1])*u; y=a[2]+(b[2]-a[2])*u; z=a[3]+(b[3]-a[3])*u;
                    break;
                }
            }
        }
         
        if (side==Side.RIGHT) { y=-y; z=-z; }
        double rad=Math.PI/180;
        return new Angles((float)(-x*rad),(float)(-y*rad),(float)(z*rad));
    }

     
    public static Override apply(PlayerModel<?> model,Side side,double seconds) {
        ModelPart arm=side==Side.LEFT?model.leftArm:model.rightArm;
        ModelPart sleeve=side==Side.LEFT?model.leftSleeve:model.rightSleeve;
        Override saved=new Override(arm,sleeve);
        Angles value=standardArmRotation(side,seconds);
        set(arm,value); set(sleeve,value);
        return saved;
    }
    private static void set(ModelPart part,Angles value) {
        part.xRot=value.xRot; part.yRot=value.yRot; part.zRot=value.zRot;
    }
    private static Angles get(ModelPart part) { return new Angles(part.xRot,part.yRot,part.zRot); }
    public static final class Override implements AutoCloseable {
        private final ModelPart arm,sleeve;
        private final Angles beforeArm,beforeSleeve;
        private boolean closed;
        private Override(ModelPart arm,ModelPart sleeve) {
            this.arm=arm; this.sleeve=sleeve; beforeArm=get(arm); beforeSleeve=get(sleeve);
        }
        public void close() {
            if (!closed) { set(arm,beforeArm); set(sleeve,beforeSleeve); closed=true; }
        }
    }
}

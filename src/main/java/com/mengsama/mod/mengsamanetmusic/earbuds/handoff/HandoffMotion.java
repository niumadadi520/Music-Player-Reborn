package com.mengsama.mod.mengsamanetmusic.earbuds.handoff;

import java.util.Optional;

 
public final class HandoffMotion {
    public static final double CONTACT = .55, EXTRACTED = .95, APPROACH = 2.15;
    public static final double RELEASE = 2.65, RETRACTED = 3.40, END = 3.60;
    private static final double OUT = .24, GRIP = .19, HAND_RADIUS = .12;
    private HandoffMotion() {}

     
    public enum Side {
        LEFT(1), RIGHT(-1);
        public final int vanillaXSign;
        Side(int sign) { vanillaXSign = sign; }
        public Vec anchorLocal() { return new Vec(vanillaXSign * 3.72 / 16, -3.9 / 16, 0); }
        public Vec wireExitLocal() { return new Vec(vanillaXSign * 4.28875 / 16, -3.029 / 16, 0); }
    }
    public enum Phase { REACH, EXTRACT, PASS, INSERT, RETRACT, HOLD }

    public record Vec(double x, double y, double z) {
        public Vec add(Vec b) { return new Vec(x+b.x, y+b.y, z+b.z); }
        public Vec subtract(Vec b) { return new Vec(x-b.x, y-b.y, z-b.z); }
        public Vec multiply(double n) { return new Vec(x*n, y*n, z*n); }
        public double dot(Vec b) { return x*b.x+y*b.y+z*b.z; }
        public Vec cross(Vec b) { return new Vec(y*b.z-z*b.y, z*b.x-x*b.z, x*b.y-y*b.x); }
        public double length() { return Math.sqrt(dot(this)); }
        public Vec unit() { double n=length(); return n>1e-12 ? multiply(1/n) : new Vec(0,0,0); }
        public boolean finite() { return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z); }
        public Vec lerp(Vec b, double t) { return multiply(1-t).add(b.multiply(t)); }
    }

     
    public record HeadPose(Vec origin, Vec xAxis, Vec yAxis, Vec zAxis, double scale) {
        public Vec point(Vec local) {
            return origin.add(xAxis.multiply(local.x*scale)).add(yAxis.multiply(local.y*scale))
                    .add(zAxis.multiply(local.z*scale));
        }
        public Vec local(Vec common) {
            Vec p=common.subtract(origin);
            return new Vec(p.dot(xAxis)/scale, p.dot(yAxis)/scale, p.dot(zAxis)/scale);
        }
        public Vec center() { return point(new Vec(0,-4.0/16,0)); }
        public Vec anchor(Side side) { return point(side.anchorLocal()); }
        public Vec outward(Side side) { return xAxis.multiply(side.vanillaXSign); }
        public boolean valid() {
            return origin.finite() && xAxis.finite() && yAxis.finite() && zAxis.finite()
                    && scale>=.8 && scale<=1.2
                    && Math.abs(xAxis.length()-1)<1e-4 && Math.abs(yAxis.length()-1)<1e-4
                    && Math.abs(zAxis.length()-1)<1e-4 && Math.abs(xAxis.dot(yAxis))<1e-4
                    && Math.abs(xAxis.dot(zAxis))<1e-4 && Math.abs(yAxis.dot(zAxis))<1e-4
                    && xAxis.cross(yAxis).dot(zAxis)>.999;
        }
         
        public boolean intersects(Vec common, double sphereRadius) {
            Vec p=local(common);
            double dx=Math.max(0,Math.abs(p.x)-4.5/16);
            double dy=Math.max(0,Math.abs(p.y+4.0/16)-4.5/16);
            double dz=Math.max(0,Math.abs(p.z)-4.5/16);
            double r=sphereRadius/scale;
            return dx*dx+dy*dy+dz*dz < r*r-1e-10;
        }
    }

    public record Visibility(boolean sourceEar, boolean receiverEar, boolean movingEar) {
        public int visibleCopies() { return (sourceEar?1:0)+(receiverEar?1:0)+(movingEar?1:0); }
    }
     
    public record Sample(boolean valid, String reason, Phase phase, Vec earPosition,
                         Vec handTarget, double orientationBlend, Visibility visibility,
                         boolean releaseReached, boolean finished, double lowArcDepth) {}

    private record Route(Vec a, Vec b, Vec aOut, Vec bOut, Vec c1, Vec c2,
                         Vec nA, Vec nB, Vec up, double depth) {
        Vec transfer(double u) {
            double v=1-u;
            return aOut.multiply(v*v*v).add(c1.multiply(3*v*v*u))
                    .add(c2.multiply(3*v*u*u)).add(bOut.multiply(u*u*u));
        }
        Vec normal(double u) {
             
            Vec hA=nA.subtract(up.multiply(nA.dot(up))).unit();
            Vec hB=nB.subtract(up.multiply(nB.dot(up))).unit();
            double angle=Math.atan2(up.dot(hA.cross(hB)), hA.dot(hB));
            if (Math.abs(Math.abs(angle)-Math.PI)<1e-6) angle=Math.PI;
            double theta=angle*u;
            Vec horizontal=hA.multiply(Math.cos(theta)).add(up.cross(hA).multiply(Math.sin(theta)));
            double vertical=nA.dot(up)*(1-u)+nB.dot(up)*u;
            return horizontal.multiply(Math.sqrt(Math.max(0,1-vertical*vertical)))
                    .add(up.multiply(vertical)).unit();
        }
        Vec grip(Vec ear, double u) { return ear.add(normal(u).multiply(GRIP)); }
    }

    public static Sample sample(HeadPose source, HeadPose receiver, Side side,
                                double seconds, Vec commonUp) {
        String problem=problem(source,receiver,commonUp,seconds);
        if (problem!=null) return cancelled(source,side,problem);
        Optional<Route> candidate=route(source,receiver,side,commonUp.unit());
        if (candidate.isEmpty()) return cancelled(source,side,"No safe chest-height route for these head poses");
        Route r=candidate.get();
        double t=Math.max(0,seconds), u; Vec ear; Phase phase;
        if (t<CONTACT) { phase=Phase.REACH; u=0; ear=r.a; }
        else if (t<EXTRACTED) {
            phase=Phase.EXTRACT; u=0; ear=r.a.lerp(r.aOut,smooth((t-CONTACT)/(EXTRACTED-CONTACT)));
        } else if (t<APPROACH) {
            phase=Phase.PASS; u=smooth((t-EXTRACTED)/(APPROACH-EXTRACTED)); ear=r.transfer(u);
        } else if (t<RELEASE) {
            phase=Phase.INSERT; u=1; ear=r.bOut.lerp(r.b,smooth((t-APPROACH)/(RELEASE-APPROACH)));
        } else { phase=t<RETRACTED?Phase.RETRACT:Phase.HOLD; u=1; ear=r.b; }
        Visibility visibility=t<CONTACT?new Visibility(true,false,false):
                t<RELEASE?new Visibility(false,false,true):new Visibility(false,true,false);
        return new Sample(true,"",phase,ear,r.grip(ear,u),u,visibility,t>=RELEASE,t>=END,r.depth);
    }

    private static String problem(HeadPose a,HeadPose b,Vec up,double seconds) {
        if (a==null || b==null || !a.valid() || !b.valid()) return "Missing/invalid head transform or unsupported player scale";
        if (!Double.isFinite(seconds) || up==null || !up.finite() || up.length()<.9) return "Invalid animation clock/up direction";
        double distance=a.center().subtract(b.center()).length();
        if (distance<.65 || distance>1.5) return "Players must remain 0.65 to 1.5 blocks apart at head height";
        Vec n=up.unit();
        if (a.yAxis.multiply(-1).dot(n)<.85 || b.yAxis.multiply(-1).dot(n)<.85)
            return "Head pitch/roll exceeds supported standing pose";
        if (Math.abs(a.center().subtract(b.center()).dot(n))>.25) return "Players must have similar head heights";
        return null;
    }

    private static Optional<Route> route(HeadPose a,HeadPose b,Side side,Vec up) {
        Vec p=a.anchor(side), q=b.anchor(side), nA=a.outward(side), nB=b.outward(side);
        Vec pOut=p.add(nA.multiply(OUT)), qOut=q.add(nB.multiply(OUT));
         
        for (double depth:new double[]{.60,.68,.76}) {
            Route r=new Route(p,q,pOut,qOut,pOut.add(nA.multiply(.12)).subtract(up.multiply(depth)),
                    qOut.add(nB.multiply(.12)).subtract(up.multiply(depth)),nA,nB,up,depth);
            boolean safe=true;
            for (int i=0;i<=80 && safe;i++) {
                double u=i/80.0;
                Vec e=r.transfer(u), palm=r.grip(e,u);
                safe=!a.intersects(e,.075) && !b.intersects(e,.075)
                        && !a.intersects(palm,HAND_RADIUS) && !b.intersects(palm,HAND_RADIUS);
                Vec extract=p.lerp(pOut,u), insert=q.lerp(qOut,u);
                 
                safe &= !b.intersects(extract,.075) && !a.intersects(insert,.075)
                        && !a.intersects(r.grip(extract,0),HAND_RADIUS)
                        && !b.intersects(r.grip(extract,0),HAND_RADIUS)
                        && !a.intersects(r.grip(insert,1),HAND_RADIUS)
                        && !b.intersects(r.grip(insert,1),HAND_RADIUS);
            }
            if (safe) return Optional.of(r);
        }
        return Optional.empty();
    }

    public static Sample cancelled(HeadPose source,Side side,String reason) {
        Vec position=source!=null && source.valid()?source.anchor(side):new Vec(0,0,0);
        return new Sample(false,reason,Phase.HOLD,position,position,0,
                new Visibility(true,false,false),false,true,0);
    }
    public static double smooth(double t) { t=Math.max(0,Math.min(1,t)); return t*t*(3-2*t); }
}

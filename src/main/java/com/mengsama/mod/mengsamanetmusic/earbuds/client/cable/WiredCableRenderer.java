package com.mengsama.mod.mengsamanetmusic.earbuds.client.cable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.model.geom.ModelPart;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.List;
import com.mengsama.mod.mengsamanetmusic.earbuds.client.cable.CableCurve.Point;

 




public final class WiredCableRenderer {
    private WiredCableRenderer() {}
    public record Segment(Point start, Point end, double sag) {}
    public record Endpoints(Point leftEar, Point rightEar, Point splitter,
                            List<Point> guidesToPlug, Point plug, Point gravity) {}

     
    public static Point capture(PoseStack pose, double x, double y, double z) {
        Vector3f p = pose.last().pose().transformPosition(new Vector3f((float)x, (float)y, (float)z));
        return new Point(p.x, p.y, p.z);
    }

     
    public static Point captureEar(PoseStack playerModelPose, ModelPart vanillaHead, boolean left) {
        playerModelPose.pushPose();
        vanillaHead.translateAndRotate(playerModelPose);
        Point tip = capture(playerModelPose, (left ? 4.28875 : -4.28875) / 16.0, -3.029 / 16.0, 0);
        playerModelPose.popPose();
        return tip;
    }

     
    public static Point captureDirection(PoseStack pose, double x, double y, double z) {
        Vector3f d = pose.last().pose().transformDirection(new Vector3f((float)x, (float)y, (float)z));
        return new Point(d.x, d.y, d.z).unit();
    }

     
    public static void render(PoseStack pose, MultiBufferSource buffers, Endpoints ends,
                              double ageTicks, double motionAmount, int packedLight) {
        VertexConsumer vertices = buffers.getBuffer(RenderType.leash());
        double sway = Math.min(0.010, Math.max(0, motionAmount) * 0.009);
        double phase = ageTicks * 0.17;
        if (ends.leftEar != null)
            cable(pose, vertices, new Segment(ends.leftEar, ends.splitter, 0.045), ends.gravity, sway, phase, packedLight);
        if (ends.rightEar != null)
            cable(pose, vertices, new Segment(ends.rightEar, ends.splitter, 0.045), ends.gravity, sway, phase+0.55, packedLight);
        Point last = ends.splitter;
        for (Point guide : ends.guidesToPlug) {
            cable(pose, vertices, new Segment(last, guide, 0.025), ends.gravity, sway, phase, packedLight);
            last = guide;
        }
        cable(pose, vertices, new Segment(last, ends.plug, 0.055), ends.gravity, sway, phase, packedLight);
    }

     
    public static void renderSegments(PoseStack pose, MultiBufferSource buffers, List<Segment> segments,
                                      Point gravity, double ageTicks, double motionAmount, int packedLight) {
        renderSegments(pose, buffers, segments, gravity, ageTicks, motionAmount, packedLight, false);
    }
    public static void renderSegments(PoseStack pose, MultiBufferSource buffers, List<Segment> segments,
                                      Point gravity, double ageTicks, double motionAmount, int packedLight, boolean warning) {
        VertexConsumer vertices = buffers.getBuffer(RenderType.leash());
        double sway = Math.min(0.010, Math.max(0, motionAmount) * 0.009);
        for (int i = 0; i < segments.size(); i++)
            cable(pose, vertices, segments.get(i), gravity, sway, ageTicks*0.17 + i*0.19, packedLight, warning);
    }

    private static void cable(PoseStack pose, VertexConsumer out, Segment segment, Point gravity,
                              double sway, double phase, int light) {
        cable(pose, out, segment, gravity, sway, phase, light, false);
    }
    private static void cable(PoseStack pose, VertexConsumer out, Segment segment, Point gravity,
                              double sway, double phase, int light, boolean warning) {
        double length = segment.end.subtract(segment.start).length();
        if (length < 0.001 || length > 5.5) return;  
        int steps = Math.max(8, Math.min(28, (int)Math.ceil(length*32)));
        Point previous = CableCurve.sample(segment.start, segment.end, gravity, segment.sag, sway, phase, 0);
        for (int step=1; step<=steps; step++) {
            Point current = CableCurve.sample(segment.start, segment.end, gravity, segment.sag, sway, phase, (double)step/steps);
            Point tangent = current.subtract(previous).unit();
            Point right = tangent.cross(new Point(0, 1, 0));
            if (right.length() < 0.001) right = tangent.cross(new Point(1, 0, 0));
            right = right.unit().scale(0.0036);  
            Point up = tangent.cross(right).unit().scale(0.0036);
            for (int side=0; side<6; side++) {
                double a = side*Math.PI/3, b=(side+1)*Math.PI/3;
                Point ra = right.scale(Math.cos(a)).add(up.scale(Math.sin(a)));
                Point rb = right.scale(Math.cos(b)).add(up.scale(Math.sin(b)));
                Point p0=previous.add(ra), p1=previous.add(rb), p2=current.add(ra), p3=current.add(rb);
                float shade = side < 3 ? 1.0f : 0.83f;
                 
                vertex(out, pose.last().pose(), p0, shade, light, warning);
                vertex(out, pose.last().pose(), p0, shade, light, warning);
                vertex(out, pose.last().pose(), p1, shade, light, warning);
                vertex(out, pose.last().pose(), p2, shade, light, warning);
                vertex(out, pose.last().pose(), p3, shade, light, warning);
                vertex(out, pose.last().pose(), p3, shade, light, warning);
            }
            previous = current;
        }
    }
    private static void vertex(VertexConsumer out, Matrix4f matrix, Point point, float shade, int light, boolean warning) {
        out.vertex(matrix, (float)point.x(), (float)point.y(), (float)point.z())
                .color((warning?1.0f:0.79f)*shade, (warning?.76f:.53f)*shade, (warning?.30f:.53f)*shade, 1.0f).uv2(light).endVertex();
    }
}

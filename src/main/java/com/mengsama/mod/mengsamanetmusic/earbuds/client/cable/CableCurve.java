package com.mengsama.mod.mengsamanetmusic.earbuds.client.cable;

 
public final class CableCurve {
    private CableCurve() {}
    public record Point(double x, double y, double z) {
        public Point add(Point b) { return new Point(x + b.x, y + b.y, z + b.z); }
        public Point subtract(Point b) { return new Point(x - b.x, y - b.y, z - b.z); }
        public Point scale(double k) { return new Point(x * k, y * k, z * k); }
        public double length() { return Math.sqrt(x*x + y*y + z*z); }
        public Point unit() { double d = length(); return d < 1e-10 ? new Point(0, -1, 0) : scale(1 / d); }
        public Point cross(Point b) { return new Point(y*b.z-z*b.y, z*b.x-x*b.z, x*b.y-y*b.x); }
    }
    public static Point sample(Point start, Point end, Point gravity, double sag,
                               double sway, double phase, double t) {
        t = Math.max(0, Math.min(1, t));
         
        if (t == 0) return start;
        if (t == 1) return end;
        Point down = gravity.unit();
        Point side = end.subtract(start).cross(down);
        if (side.length() < 1e-8) side = down.cross(new Point(1, 0, 0));
        if (side.length() < 1e-8) side = down.cross(new Point(0, 0, 1));
        double envelope = 4*t*(1-t);
        return start.scale(1-t).add(end.scale(t))
                .add(down.scale(Math.max(0, sag) * envelope))
                .add(side.unit().scale(sway * Math.sin(phase + t*1.3) * envelope));
    }
}

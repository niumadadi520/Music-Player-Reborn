package com.mengsama.mod.mengsamanetmusic.compat.backpack.charm;

 




public final class CharmDynamics {
    private static final double DT = 1.0 / 20.0;
    private static final double RAD = Math.PI / 180.0;
    private final Spring pitch = new Spring(11.5, 0.34, -2.5, 16.0);
    private final Spring roll = new Spring(11.0, 0.30, -14.0, 14.0);
    private final Spring pendantPitch = new Spring(16.0, 0.40, -1.0, 3.0);
    private final Spring pendantRoll = new Spring(15.0, 0.37, -3.0, 3.0);
    private final Spring pendantYaw = new Spring(13.5, 0.34, -6.0, 6.0);
    private Pose previous = Pose.ZERO;
    private Pose current = Pose.ZERO;
    private boolean initialized;
    private long lastTick;
    private double lastX, lastY, lastZ, lastYaw, lastVx, lastVy, lastVz;
    private boolean lastGround, lastCrouching;
    private double walkPhase;

    public record Pose(double chainPitch, double chainRoll, double pendantPitch,
                       double pendantRoll, double pendantYaw) {
        public static final Pose ZERO = new Pose(0, 0, 0, 0, 0);
    }

    public void sample(long tick, double x, double y, double z, double yawDegrees,
                       boolean grounded, boolean crouching) {
        sample(tick, x, y, z, yawDegrees, grounded, crouching, 0);
    }

    public void sample(long tick, double x, double y, double z, double yawDegrees,
                       boolean grounded, boolean crouching, double extraOutwardOffsetBlocks) {
        if (!Double.isFinite(x + y + z + yawDegrees + extraOutwardOffsetBlocks)) {
            reset();
            return;
        }
         
         
        double yaw = yawDegrees * RAD;
        double depth = 0.6875 + extraOutwardOffsetBlocks;
        double anchorX = x - 0.234375 * Math.cos(yaw) + depth * Math.sin(yaw);
        double anchorZ = z - 0.234375 * Math.sin(yaw) - depth * Math.cos(yaw);
        double dx = anchorX - lastX;
        double dy = y - lastY;
        double dz = anchorZ - lastZ;
        if (!initialized || tick != lastTick + 1 || dx * dx + dy * dy + dz * dz > 64.0) {
            reset();
            initialized = true;
            remember(tick, anchorX, y, anchorZ, yaw, grounded, crouching);
            return;
        }
        previous = current;
        double vx = dx / DT, vy = dy / DT, vz = dz / DT;
        double ax = clamp((vx - lastVx) / DT, -40, 40);
        double ay = clamp((vy - lastVy) / DT, -50, 50);
        double az = clamp((vz - lastVz) / DT, -40, 40);
         
        double localAx = Math.cos(yaw) * ax + Math.sin(yaw) * az;
        double localAz = -Math.sin(yaw) * ax + Math.cos(yaw) * az;
        double yawVelocity = wrapRadians(yaw - lastYaw) / DT;
        double horizontalSpeed = Math.hypot(vx, vz);
        walkPhase += Math.min(horizontalSpeed, 6.0) * DT * 5.0;
         
        double gait = grounded ? Math.sin(walkPhase * 2.0) * Math.min(horizontalSpeed, 5.0) : 0;
        if (lastGround && !grounded) {
            pitch.velocity += 0.34;
        } else if (!lastGround && grounded) {
            pitch.velocity += clamp(Math.abs(lastVy) * 0.17, 0.12, 1.15);
            pendantPitch.velocity += 0.15;
        }
        if (lastCrouching != crouching) {
            pitch.velocity += crouching ? 0.25 : -0.12;
        }
        double gravityScale = clamp(1.0 + ay / 55.0, 0.6, 1.7);
         
        for (int i = 0; i < 4; i++) {
            double substep = DT / 4.0;
            pitch.step(localAz * 2.1 + gait * 0.30, gravityScale, substep);
            roll.step(-localAx * 2.1 + gait * 1.4, gravityScale, substep);
            pendantPitch.step(-pitch.velocity * 3.2 + localAz * 0.18, 1, substep);
            pendantRoll.step(-roll.velocity * 3.5 - localAx * 0.18, 1, substep);
            pendantYaw.step(-yawVelocity * 7.0, 1, substep);
        }
        current = new Pose(pitch.angle / RAD, roll.angle / RAD, pendantPitch.angle / RAD,
                pendantRoll.angle / RAD, pendantYaw.angle / RAD);
        lastVx = vx;
        lastVy = vy;
        lastVz = vz;
        remember(tick, anchorX, y, anchorZ, yaw, grounded, crouching);
    }

    public Pose interpolated(double partialTick) {
        double t = clamp(partialTick, 0, 1);
        return new Pose(lerp(previous.chainPitch, current.chainPitch, t),
                lerp(previous.chainRoll, current.chainRoll, t),
                lerp(previous.pendantPitch, current.pendantPitch, t),
                lerp(previous.pendantRoll, current.pendantRoll, t),
                lerp(previous.pendantYaw, current.pendantYaw, t));
    }

    public void reset() {
        pitch.reset();
        roll.reset();
        pendantPitch.reset();
        pendantRoll.reset();
        pendantYaw.reset();
        previous = current = Pose.ZERO;
        initialized = false;
        lastVx = lastVy = lastVz = walkPhase = 0;
    }

    private void remember(long tick, double x, double y, double z, double yaw,
                          boolean grounded, boolean crouching) {
        lastTick = tick;
        lastX = x;
        lastY = y;
        lastZ = z;
        lastYaw = yaw;
        lastGround = grounded;
        lastCrouching = crouching;
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    private static double wrapRadians(double angle) {
        return Math.atan2(Math.sin(angle), Math.cos(angle));
    }

    private static double clamp(double value, double low, double high) {
        return Math.max(low, Math.min(high, value));
    }

    private static final class Spring {
        private final double omega, damping, low, high;
        private double angle, velocity;

        private Spring(double omega, double damping, double low, double high) {
            this.omega = omega;
            this.damping = damping;
            this.low = low * RAD;
            this.high = high * RAD;
        }

        private void step(double force, double gravity, double dt) {
            velocity += (force - omega * omega * gravity * angle - 2 * damping * omega * velocity) * dt;
            angle += velocity * dt;
            if (angle < low) {
                angle = low;
                velocity = Math.max(0, velocity) * 0.12;
            } else if (angle > high) {
                angle = high;
                velocity = Math.min(0, velocity) * 0.12;
            }
            if (Math.abs(angle) < 1.0e-7 && Math.abs(velocity) < 1.0e-6 && Math.abs(force) < 1.0e-6) {
                angle = velocity = 0;
            }
        }

        private void reset() {
            angle = velocity = 0;
        }
    }
}

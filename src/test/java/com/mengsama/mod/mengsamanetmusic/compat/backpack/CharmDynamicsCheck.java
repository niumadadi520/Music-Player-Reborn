package com.mengsama.mod.mengsamanetmusic.compat.backpack;
import com.mengsama.mod.mengsamanetmusic.compat.backpack.charm.CharmDynamics;

import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

 
public final class CharmDynamicsCheck {
    public static void main(String[] args) throws Exception {
        CharmDynamics rest = new CharmDynamics();
        for (int i = 0; i < 200; i++) {
            rest.sample(i, 0, 0, 0, 0, true, false);
        }
        require(magnitude(rest.interpolated(1)) == 0, "A stationary attachment must not create motion");

        CharmDynamics dynamics = new CharmDynamics();
        double z = 0, x = 0, yaw = 0;
        double motionPeak = 0, turnPeak = 0, jumpPeak = 0;
        PrintWriter csv = args.length == 0 ? null : new PrintWriter(Path.of(args[0]).toFile(), StandardCharsets.UTF_8);
        if (csv != null) {
            csv.println("tick,time_seconds,stage,x,y,z,yaw,chain_pitch,chain_roll,pendant_pitch,pendant_roll,pendant_yaw");
        }
        for (int tick = 0; tick < 350; tick++) {
            String stage = "settle";
            double y = 0;
            boolean grounded = true;
            if (tick < 20) {
                stage = "idle";
            } else if (tick < 70) {
                stage = "walk";
                z += .085;
                x += .009;
            } else if (tick < 90) {
                stage = "stop";
                z += .085 * (89 - tick) / 20.0;
            } else if (tick < 130) {
                stage = "turn";
                yaw += 2.25;
            } else if (tick >= 150 && tick < 170) {
                stage = "jump_land";
                double t = (tick - 149) / 20.0;
                y = 2.8 * t * (1.0 - t);
                grounded = tick == 169;
            }
            dynamics.sample(tick, x, y, z, yaw, grounded, false);
            CharmDynamics.Pose pose = dynamics.interpolated(1);
            checkBounds(pose);
            if (tick >= 20 && tick < 90) motionPeak = Math.max(motionPeak, magnitude(pose));
            if (tick >= 90 && tick < 130) turnPeak = Math.max(turnPeak, Math.abs(pose.pendantYaw()));
            if (tick >= 150 && tick < 180) jumpPeak = Math.max(jumpPeak, Math.abs(pose.chainPitch()));
             
            require(dynamics.interpolated(.5).equals(dynamics.interpolated(.5)), "Sampling mutated the solver");
            if (csv != null) {
                csv.printf(java.util.Locale.ROOT, "%d,%.2f,%s,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f%n",
                        tick, tick / 20.0, stage, x, y, z, yaw, pose.chainPitch(), pose.chainRoll(),
                        pose.pendantPitch(), pose.pendantRoll(), pose.pendantYaw());
            }
        }
        if (csv != null) csv.close();
        require(motionPeak > 1, "Movement failed to excite the chain");
        require(turnPeak > .5, "Turning failed to excite pendant twist");
        require(jumpPeak > .5, "Jump and landing failed to excite the chain");
        require(magnitude(dynamics.interpolated(1)) < .001, "Oscillation did not decay after stopping");

        CharmDynamics extreme = new CharmDynamics();
        for (int tick = 0; tick < 2000; tick++) {
            extreme.sample(tick, Math.sin(tick * 2) * 1.5, Math.sin(tick * 3) * .8,
                    Math.cos(tick * 2) * 1.5, tick * 150.0, tick % 3 == 0, tick % 7 == 0);
            checkBounds(extreme.interpolated(0));
            checkBounds(extreme.interpolated(.37));
            checkBounds(extreme.interpolated(1));
        }
        extreme.sample(2000, 1000, 1000, 1000, 0, true, false);
        require(magnitude(extreme.interpolated(1)) == 0, "Teleport should reset accumulated inertia");
        extreme.sample(2005, 1000, 1000, 1000, 0, true, false);
        require(magnitude(extreme.interpolated(1)) == 0, "Tick gap should reset accumulated inertia");
        extreme.sample(2006, Double.NaN, 0, 0, 0, true, false);
        require(magnitude(extreme.interpolated(1)) == 0, "Non-finite input must safely reset");
        System.out.printf(java.util.Locale.ROOT,
                "PASS: rest, movement, turning, jump/landing, interpolation, decay, 2000 extreme ticks, teleport, tick-gap, non-finite reset.%n"
                        + "Movement peak %.3f deg; turn twist peak %.3f deg; jump/landing peak %.3f deg.%n",
                motionPeak, turnPeak, jumpPeak);
    }

    private static double magnitude(CharmDynamics.Pose p) {
        return Math.max(Math.abs(p.chainPitch()), Math.max(Math.abs(p.chainRoll()),
                Math.max(Math.abs(p.pendantPitch()), Math.max(Math.abs(p.pendantRoll()), Math.abs(p.pendantYaw())))));
    }

    private static void checkBounds(CharmDynamics.Pose p) {
        between(p.chainPitch(), -2.5, 16);
        between(p.chainRoll(), -14, 14);
        between(p.pendantPitch(), -1, 3);
        between(p.pendantRoll(), -3, 3);
        between(p.pendantYaw(), -6, 6);
    }

    private static void between(double value, double min, double max) {
        require(Double.isFinite(value) && value >= min - 1.0e-8 && value <= max + 1.0e-8, "Limit violated: " + value);
    }

    private static void require(boolean pass, String message) {
        if (!pass) throw new AssertionError(message);
    }
}

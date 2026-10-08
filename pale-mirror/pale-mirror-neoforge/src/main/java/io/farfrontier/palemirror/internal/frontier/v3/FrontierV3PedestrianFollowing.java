package io.farfrontier.palemirror.internal.frontier.v3;

import net.minecraft.world.phys.Vec3;

/** Geometric co-flow and headway, independent of activity, group or facility. */
final class FrontierV3PedestrianFollowing {
    private FrontierV3PedestrianFollowing() { }
    static boolean coFlow(Vec3 position, Vec3 direction, Vec3 peer, Vec3 velocity) {
        double length = Math.hypot(direction.x, direction.z);
        if (length == 0) return false;
        double x = direction.x / length, z = direction.z / length;
        double ahead = (peer.x - position.x) * x + (peer.z - position.z) * z;
        double forward = velocity.x * x + velocity.z * z;
        double across = velocity.x * z - velocity.z * x;
        return ahead > 0 && forward > Math.abs(across);
    }
    static double paceFraction(double distance, double actorWidth, double peerWidth) {
        double clearance = (actorWidth + peerWidth) / 2;
        // Body dimensions define both collision clearance and one-body-length headway.
        // Following changes input, not the accepted path or its progress deadline.
        return Math.clamp((distance - clearance) / actorWidth, 0, 1);
    }
}

package io.farfrontier.palemirror.internal.frontier.v3;

import net.minecraft.world.phys.Vec3;

/** Geometric co-flow and headway, independent of activity, group or facility. */
final class FrontierV3PedestrianFollowing {
    private FrontierV3PedestrianFollowing() { }
    static boolean crossing(Vec3 direction, Vec3 velocity) {
        double forward = velocity.x * direction.x + velocity.z * direction.z;
        double across = velocity.x * direction.z - velocity.z * direction.x;
        return Math.abs(across) > Math.abs(forward);
    }
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
        return io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianHeadway.paceFraction(distance, actorWidth, peerWidth);
    }
}

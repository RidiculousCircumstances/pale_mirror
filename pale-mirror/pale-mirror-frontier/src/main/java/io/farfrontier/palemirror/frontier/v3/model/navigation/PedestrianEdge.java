package io.farfrontier.palemirror.frontier.v3.model.navigation;

import io.farfrontier.palemirror.frontier.v3.model.SurfaceAnchor;

/** Encoding bounds only. Actual permission requires current collision contacts and the actor profile. */
public final class PedestrianEdge {
    private PedestrianEdge() { }
    // Supported shapes extend from -1 to +2 relative to their block; profiles
    // permit at most three blocks of descent. Semantic block Y is not feet Y.
    public static boolean boundedDelta(long dx, long dy, long dz) {
        return Math.abs(dx) + Math.abs(dz) == 1 && Math.abs(dy) <= 6;
    }
    public static boolean adjacent(SurfaceAnchor from, SurfaceAnchor to) {
        return boundedDelta((long) to.x() - from.x(), (long) to.y() - from.y(), (long) to.z() - from.z());
    }
}

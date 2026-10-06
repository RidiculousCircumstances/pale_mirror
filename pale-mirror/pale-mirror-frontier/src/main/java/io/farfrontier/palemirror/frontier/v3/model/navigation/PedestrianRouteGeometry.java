package io.farfrontier.palemirror.frontier.v3.model.navigation;

import io.farfrontier.palemirror.frontier.v3.model.SurfaceAnchor;
import io.farfrontier.palemirror.frontier.v3.model.WorldBounds;

/** Immutable read-only navigation knowledge. Null support means unknown, never open ground. */
public interface PedestrianRouteGeometry {
    /** Derived immutable-world version only: a cache/cancellation token, never execution authority. */
    default Object version() { return this; }
    WorldBounds bounds();
    SurfaceAnchor supportAt(int x, int z);
    boolean blocked(SurfaceAnchor surface);
}

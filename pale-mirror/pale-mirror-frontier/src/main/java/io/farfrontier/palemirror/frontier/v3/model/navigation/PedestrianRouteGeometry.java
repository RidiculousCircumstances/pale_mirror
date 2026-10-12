package io.farfrontier.palemirror.frontier.v3.model.navigation;

import io.farfrontier.palemirror.frontier.v3.model.SurfaceAnchor;
import io.farfrontier.palemirror.frontier.v3.model.WorldBounds;

/** Immutable read-only navigation knowledge. Null support means unknown, never open ground. */
public interface PedestrianRouteGeometry {
    /** Derived immutable-world version only: a cache/cancellation token, never execution authority. */
    default Object version() { return this; }
    /** Explicit compatible scope and per-region revisions; absent providers invalidate conservatively. */
    default java.util.Optional<PedestrianGeometryRevision> regionalRevision() { return java.util.Optional.empty(); }
    WorldBounds bounds();
    SurfaceAnchor supportAt(int x, int z);
    boolean blocked(SurfaceAnchor surface);

    /** Shared adult-pedestrian edge policy; both endpoints must independently be clear supports.
     * A one-block step also needs the upper body clearance over its lower support.
     * This derived query grants no route, arrival, unknown terrain or execution authority. */
    default boolean canTraverse(SurfaceAnchor from, SurfaceAnchor to) {
        if (Math.abs((long) from.x() - to.x()) + Math.abs((long) from.z() - to.z()) != 1
                || Math.abs((long) from.y() - to.y()) > 1) return false;
        if (from.y() == to.y()) return true;
        SurfaceAnchor lower = from.y() < to.y() ? from : to;
        return !blocked(SurfaceAnchor.at(lower.x(), Math.max(from.y(), to.y()), lower.z()));
    }
}

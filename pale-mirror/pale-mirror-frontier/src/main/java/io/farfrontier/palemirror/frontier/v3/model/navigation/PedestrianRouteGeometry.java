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
    /** Exact levels, not a heightmap. Empty means missing column knowledge. */
    default java.util.List<SurfaceAnchor> surfacesAt(int x, int z) {
        var surface = supportAt(x, z);
        return surface == null ? java.util.List.of() : java.util.List.of(surface);
    }
    default boolean knowsSupport(SurfaceAnchor surface) {
        return surfacesAt(surface.x(), surface.z()).contains(surface);
    }
    boolean blocked(SurfaceAnchor surface);
    /** Missing current block facts are a wait, not proof that a route is impossible. */
    default boolean clearanceKnown(SurfaceAnchor surface) { return true; }
    default boolean blockKnown(io.farfrontier.palemirror.frontier.v3.model.BlockPosition position) { return true; }
    /** Positive knowledge that this exact support was removed or forbidden, not missing knowledge. */
    default boolean supportExcluded(SurfaceAnchor surface) { return false; }
    /** Body clearance does not require a floor at the body's intermediate swept height. */
    default boolean clearBody(SurfaceAnchor surface) { return !blocked(surface); }
    /** Support exclusion is separate from a particular reference body's clearance. */
    default boolean supportAvailable(SurfaceAnchor surface) { return !blocked(surface); }
    /** A transit node is not necessarily a place where the complete body can centre. */
    default boolean clearStation(SurfaceAnchor surface) { return !blocked(surface); }
    /** Historical birth origin need not be clear. This edge grants placement geometry, never walking. */
    default boolean canProjectPlacement(SurfaceAnchor retained, SurfaceAnchor target) {
        return Math.abs((long) retained.x() - target.x()) + Math.abs((long) retained.z() - target.z()) == 1
                && Math.abs((long) retained.y() - target.y()) <= 1
                && bounds().contains(target.support()) && knowsSupport(target) && !blocked(target);
    }

    /** Shared adult-pedestrian edge policy; both endpoints must independently be clear supports.
     * A one-block step also needs the upper body clearance over its lower support.
     * This derived query grants no route, arrival, unknown terrain or execution authority. */
    default boolean canTraverse(SurfaceAnchor from, SurfaceAnchor to) {
        if (Math.abs((long) from.x() - to.x()) + Math.abs((long) from.z() - to.z()) != 1
                || Math.abs((long) from.y() - to.y()) > 1 || blocked(from) || blocked(to)) return false;
        if (from.y() == to.y()) return true;
        SurfaceAnchor lower = from.y() < to.y() ? from : to;
        return clearBody(SurfaceAnchor.at(lower.x(), Math.max(from.y(), to.y()), lower.z()));
    }
}

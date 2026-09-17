package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.BodyPosition;
import io.farfrontier.palemirror.frontier.v3.model.SurfaceAnchor;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

import java.util.Objects;

/**
 * Maps collision-authoritative fractional Minecraft positions back to one retained semantic
 * surface.  A body may overhang a floor edge by a few centimetres while still standing on that
 * exact support; {@link Mob#getBlockX()} then flips to the adjacent integer cell even though no
 * retained traversal edge has been reached.  This adapter accepts only that narrow footprint
 * overhang and emits the already-retained body cell.  It never rounds a body to an arbitrary
 * neighbour, chooses a route or changes canonical state itself.
 */
final class FrontierV3SurfaceObservation {
    private static final double HORIZONTAL_CAPTURE_HALF_WIDTH = 0.55D;
    private static final double VERTICAL_CAPTURE_HALF_HEIGHT = 0.35D;
    // Vanilla collision can leave a body just above the semantic capture band while it is
    // still inside the same retained support column.  Reacquisition moves it only back to that
    // already-owned surface; it must remain slightly wider than canonical arrival so the
    // actuator cannot turn this transient pose into an observed cursor advance.
    // A one-half-block physical band covers the observed collision lip while remaining below
    // a neighbouring support datum. It permits only a collision-walk back to the already
    // retained cursor; canonical arrival remains the tighter .35 semantic observation.
    private static final double VERTICAL_REACQUISITION_HALF_HEIGHT = 0.50D;
    /**
     * An exact body may be pushed one local cell by ordinary Minecraft collision between two
     * HOT observations.  The scene may walk it back only to its already-retained cursor; a
     * larger displacement remains a visible conflict rather than an implicit route choice.
     */
    private static final double HORIZONTAL_REACQUISITION_RADIUS = 1.50D;

    private FrontierV3SurfaceObservation() { }

    static boolean at(Mob body, SurfaceAnchor surface) {
        Objects.requireNonNull(body, "surface observation body");
        return at(body.position(), surface);
    }

    /** The horizontal half of one retained checkpoint observation, without cursor authority. */
    static boolean horizontallyAt(Mob body, SurfaceAnchor surface) {
        Objects.requireNonNull(body, "surface observation body");
        Objects.requireNonNull(surface, "surface observation surface");
        Vec3 target = point(surface), observed = body.position();
        return Math.abs(observed.x - target.x) <= HORIZONTAL_CAPTURE_HALF_WIDTH
                && Math.abs(observed.z - target.z) <= HORIZONTAL_CAPTURE_HALF_WIDTH;
    }

    /**
     * A body walking from one retained support to the immediately lower retained support may
     * be vertically between those two observations while its X/Z already reaches the latter.
     * This remains a physical completion predicate for that one edge; it grants no cursor
     * advance and cannot identify any third support.
     */
    static boolean withinDescendingEdgeHeights(Mob body, SurfaceAnchor current, SurfaceAnchor next) {
        Objects.requireNonNull(body, "surface observation body");
        Objects.requireNonNull(current, "surface observation current");
        Objects.requireNonNull(next, "surface observation next");
        double currentHeight = point(current).y, nextHeight = point(next).y;
        return nextHeight < currentHeight
                && body.getY() <= currentHeight + VERTICAL_CAPTURE_HALF_HEIGHT
                && body.getY() >= nextHeight - VERTICAL_CAPTURE_HALF_HEIGHT;
    }

    /**
     * A body beginning one retained ascending edge can be collision-lifted over the upper
     * support's lip while its X/Z remains in the current column.  This is not arrival at the
     * next cursor and does not make the lifted position canonical: it only allows the actuator
     * to finish that already-declared one-grade ascent instead of trying to lower the body
     * through the upper support it is visibly touching.
     */
    static boolean withinAscendingEdgeLip(Mob body, SurfaceAnchor current, SurfaceAnchor next) {
        Objects.requireNonNull(body, "surface observation body");
        return withinAscendingEdgeLip(body.position(), current, next);
    }

    /** Pure geometry form used by the narrow collision-boundary regression. */
    static boolean withinAscendingEdgeLip(Vec3 observed, SurfaceAnchor current, SurfaceAnchor next) {
        Objects.requireNonNull(observed, "surface observation position");
        Objects.requireNonNull(current, "surface observation current");
        Objects.requireNonNull(next, "surface observation next");
        double currentHeight = point(current).y, nextHeight = point(next).y;
        // The two retained observations bound this one physical edge.  Collision can lift a
        // body over the upper support before its X/Z enters that support's capture window, so
        // its legal vertical interval is the union of the two observation bands—not an
        // invented fractional pre-arrival cap.  X/Z still has to be in the current column,
        // therefore this cannot become a semantic arrival or select another route node.
        double edgeFloor = currentHeight - VERTICAL_CAPTURE_HALF_HEIGHT;
        double edgeCeiling = nextHeight + VERTICAL_CAPTURE_HALF_HEIGHT;
        return nextHeight > currentHeight
                && Math.abs(observed.x - point(current).x) <= HORIZONTAL_CAPTURE_HALF_WIDTH
                && Math.abs(observed.z - point(current).z) <= HORIZONTAL_CAPTURE_HALF_WIDTH
                && observed.y >= edgeFloor
                && observed.y <= edgeCeiling;
    }

    /** Pure geometry boundary shared by entity observation and focused tests. */
    static boolean at(Vec3 observed, SurfaceAnchor surface) {
        Objects.requireNonNull(observed, "surface observation position");
        Objects.requireNonNull(surface, "surface observation surface");
        Vec3 target = point(surface);
        return Math.abs(observed.x - target.x) <= HORIZONTAL_CAPTURE_HALF_WIDTH
                && Math.abs(observed.z - target.z) <= HORIZONTAL_CAPTURE_HALF_WIDTH
                && Math.abs(observed.y - target.y) <= VERTICAL_CAPTURE_HALF_HEIGHT;
    }

    /**
     * This deliberately does not make the observed position canonical.  It is a bounded
     * physical recovery permission to re-approach the current immutable cursor, used before a
     * scene reports a real route/body conflict.
     */
    static boolean mayReacquire(Mob body, SurfaceAnchor surface) {
        Objects.requireNonNull(body, "surface recovery body");
        return mayReacquire(body.position(), surface);
    }

    static boolean mayReacquire(Vec3 observed, SurfaceAnchor surface) {
        Objects.requireNonNull(observed, "surface recovery position");
        Objects.requireNonNull(surface, "surface recovery surface");
        Vec3 target = point(surface);
        double horizontalSquared = (observed.x - target.x) * (observed.x - target.x)
                + (observed.z - target.z) * (observed.z - target.z);
        return horizontalSquared <= HORIZONTAL_REACQUISITION_RADIUS * HORIZONTAL_REACQUISITION_RADIUS
                && Math.abs(observed.y - target.y) <= VERTICAL_REACQUISITION_HALF_HEIGHT;
    }

    static BodyPosition observedAt(Mob body, SurfaceAnchor surface) {
        if (!at(body, surface)) throw new IllegalArgumentException("body is not at its retained surface");
        return surface.standingBody();
    }

    static Vec3 point(SurfaceAnchor surface) {
        Objects.requireNonNull(surface, "surface observation point");
        return new Vec3(surface.x() + 0.5D, surface.y() + 1.0D, surface.z() + 0.5D);
    }
}

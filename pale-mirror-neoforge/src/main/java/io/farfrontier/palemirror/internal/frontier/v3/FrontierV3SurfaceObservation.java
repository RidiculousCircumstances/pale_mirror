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
                && Math.abs(observed.y - target.y) <= VERTICAL_CAPTURE_HALF_HEIGHT;
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

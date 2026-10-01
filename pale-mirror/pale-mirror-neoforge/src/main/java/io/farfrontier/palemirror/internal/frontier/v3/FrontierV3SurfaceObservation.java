package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.BodyPosition;
import io.farfrontier.palemirror.frontier.v3.model.SurfaceAnchor;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

import java.util.Objects;

/**
 * Compatibility observation facade for retained surface consumers.
 *
 * <p>A surface is no longer inferred from an X/Z/Y capture band.  A body is current only when
 * Minecraft reports the exact named collision support.  Scene executors needing
 * medium and clearance additionally use {@link FrontierV3SemanticMovement}; this facade keeps
 * non-movement ownership boundaries on the same exact support identity.</p>
 */
final class FrontierV3SurfaceObservation {
    private FrontierV3SurfaceObservation() { }

    static boolean at(Mob body, SurfaceAnchor surface) {
        Objects.requireNonNull(body, "surface observation body");
        Objects.requireNonNull(surface, "surface observation surface");
        return FrontierV3BodyObservation.capture(body).support().filter(surface::equals).isPresent();
    }

    /** Pure cell form for fixture setup only; live movement must use {@link #at(Mob, SurfaceAnchor)}. */
    static boolean at(Vec3 observed, SurfaceAnchor surface) {
        Objects.requireNonNull(observed, "surface observation position");
        Objects.requireNonNull(surface, "surface observation surface");
        return (int) Math.floor(observed.x) == surface.x()
                && (int) Math.floor(observed.y) == surface.y() + 1
                && (int) Math.floor(observed.z) == surface.z();
    }

    static BodyPosition observedAt(Mob body, SurfaceAnchor surface) {
        if (!at(body, surface)) throw new IllegalArgumentException("body is not at its retained surface");
        return surface.standingBody();
    }

    static BodyPosition observedBody(Mob body) {
        return FrontierV3BodyObservation.position(body);
    }

    static Vec3 point(SurfaceAnchor surface) { return FrontierV3SemanticMovement.point(surface); }
}

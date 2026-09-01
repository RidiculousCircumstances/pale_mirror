package io.farfrontier.palemirror.frontier.v3.model;

/**
 * Explicit coordinate conversions used by legacy-oriented fixtures.
 *
 * <p>Test data historically described an actor by the supporting block below
 * its feet.  Production code deliberately no longer accepts that ambiguous
 * value, so fixtures must state the conversion at their boundary.</p>
 */
final class FrontierTestPositions {
    private FrontierTestPositions() {
    }

    static BodyPosition bodyAboveSupport(BlockPosition support) {
        return BodyPosition.above(new SurfaceAnchor(support));
    }

    static BlockPosition supportOf(ActorLocation location) {
        return location.supportingSurface().support();
    }

    static BodyPosition bodyCellOf(ActorLocation location) {
        return location.body();
    }
}

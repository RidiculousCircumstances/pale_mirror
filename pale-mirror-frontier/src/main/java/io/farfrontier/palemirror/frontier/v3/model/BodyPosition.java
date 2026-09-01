package io.farfrontier.palemirror.frontier.v3.model;

/**
 * Integer feet-air cell of a canonical body.
 *
 * <p>It intentionally has no conversion back to a generic spatial meaning: callers must choose
 * an explicit {@link SurfaceAnchor}, facility port or transport node when they need one.</p>
 */
public record BodyPosition(int x, int y, int z) {
    public static BodyPosition above(SurfaceAnchor surface) {
        return new BodyPosition(surface.x(), Math.addExact(surface.y(), 1), surface.z());
    }
}

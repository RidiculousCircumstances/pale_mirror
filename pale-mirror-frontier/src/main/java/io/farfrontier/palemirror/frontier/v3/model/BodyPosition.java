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

    /** Converts an explicit current-domain support cell into the body cell above it. */
    public static BodyPosition aboveSupportCell(BlockPosition support) {
        return above(new SurfaceAnchor(support));
    }

    /** @deprecated Source-only transition alias; persisted formats never use this path. */
    @Deprecated(forRemoval = true)
    public static BodyPosition aboveLegacySupport(BlockPosition support) {
        return aboveSupportCell(support);
    }

    /** The one support column directly below this feet-air cell. */
    public SurfaceAnchor supportingSurface() {
        return SurfaceAnchor.at(x, Math.subtractExact(y, 1), z);
    }

    public BodyPosition offset(int deltaX, int deltaY, int deltaZ) {
        return new BodyPosition(Math.addExact(x, deltaX), Math.addExact(y, deltaY), Math.addExact(z, deltaZ));
    }
}

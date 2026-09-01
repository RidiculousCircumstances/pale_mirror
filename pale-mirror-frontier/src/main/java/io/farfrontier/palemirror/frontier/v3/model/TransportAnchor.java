package io.farfrontier.palemirror.frontier.v3.model;

import java.util.Objects;

/**
 * A stable support-column anchor for a physical carrier, wagon or other transport.
 *
 * <p>The anchor is deliberately not a body position.  The registered physical provider resolves
 * the loaded support column to its one permitted carrier placement and reports the observed
 * result; canonical routing never infers a vehicle's feet/body datum from a generic coordinate.</p>
 */
public record TransportAnchor(SurfaceAnchor surface) {
    public TransportAnchor {
        Objects.requireNonNull(surface, "transport anchor surface");
    }

    /** Creates the transport's support-column anchor from an explicit support cell. */
    public static TransportAnchor atSupportCell(BlockPosition support) {
        return new TransportAnchor(new SurfaceAnchor(support));
    }

    /** @deprecated Source-only transition alias; persisted formats never use this path. */
    @Deprecated(forRemoval = true)
    public static TransportAnchor atLegacySupport(BlockPosition support) {
        return atSupportCell(support);
    }

    public int x() { return surface.x(); }
    public int y() { return surface.y(); }
    public int z() { return surface.z(); }

    public TransportAnchor offset(int deltaX, int deltaY, int deltaZ) {
        return new TransportAnchor(surface.offset(deltaX, deltaY, deltaZ));
    }

    public boolean sharesSupportColumn(BodyPosition body) {
        Objects.requireNonNull(body, "body position");
        return x() == body.x() && z() == body.z();
    }
}

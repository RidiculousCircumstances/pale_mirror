package io.farfrontier.palemirror.frontier.v3.model;

import java.util.Objects;

/**
 * A semantic support surface in the canonical world.
 *
 * <p>This is deliberately distinct from an entity's feet.  A route, foundation, stair or
 * natural ground occupies this cell; a normal body stands in the first clear cell above it.
 * The value is pure-domain and may therefore be retained by COLD traversal without importing a
 * Minecraft heightmap or collision rule.</p>
 */
public record SurfaceAnchor(BlockPosition support) {
    public SurfaceAnchor {
        Objects.requireNonNull(support, "surface support");
    }

    public static SurfaceAnchor at(int x, int y, int z) { return new SurfaceAnchor(new BlockPosition(x, y, z)); }

    public int x() { return support.x(); }
    public int y() { return support.y(); }
    public int z() { return support.z(); }
    public SurfaceAnchor offset(int deltaX, int deltaY, int deltaZ) { return new SurfaceAnchor(support.offset(deltaX, deltaY, deltaZ)); }
    public BodyPosition standingBody() { return BodyPosition.above(this); }
}

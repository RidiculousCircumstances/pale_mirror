package io.farfrontier.palemirror.frontier.v3.model;

/** Stable outward orientation of a compiled facility port, not a Minecraft runtime direction. */
public enum FacilityFacing {
    NORTH(0, -1), EAST(1, 0), SOUTH(0, 1), WEST(-1, 0);

    private final int x;
    private final int z;

    FacilityFacing(int x, int z) { this.x = x; this.z = z; }
    public int x() { return x; }
    public int z() { return z; }
    public SurfaceAnchor step(SurfaceAnchor origin, int distance) { return origin.offset(x * distance, 0, z * distance); }
    /** One local-left step, so a compiled formation never hard-codes a global Z offset. */
    public SurfaceAnchor stepLeft(SurfaceAnchor origin, int distance) { return origin.offset(z * distance, 0, -x * distance); }
    /** One local-right step, so a compiled formation never hard-codes a global X offset. */
    public SurfaceAnchor stepRight(SurfaceAnchor origin, int distance) { return origin.offset(-z * distance, 0, x * distance); }
}

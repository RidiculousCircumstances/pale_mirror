package io.farfrontier.palemirror.frontier.v3.model.geometry;

import io.farfrontier.palemirror.frontier.v3.model.BlockPosition;
import io.farfrontier.palemirror.frontier.v3.model.SurfaceAnchor;

/** Pure fixed-point reach envelope. Geometry, not a source recipe, target selector or actuator. */
public record BlockInteractionReach(int radiusMilli, int eyeAboveSupportMilli, int horizontalSupportExtentMilli) {
    /** Current vanilla adult pedestrian physical envelope, not a work-speed or planning-radius tuning. */
    public static final BlockInteractionReach VANILLA_ADULT_PEDESTRIAN = new BlockInteractionReach(4500, 2620,
            500 + Math.ceilDiv(io.farfrontier.palemirror.frontier.v3.model.navigation.UnitNavigationProfile.VILLAGER.widthMicros(), 2000));
    public BlockInteractionReach {
        if (radiusMilli < 1 || radiusMilli > 6000 || eyeAboveSupportMilli < 1000 || eyeAboveSupportMilli > 4000
                || horizontalSupportExtentMilli < 0 || horizontalSupportExtentMilli > 3000)
            throw new IllegalArgumentException("invalid bounded block interaction reach");
    }
    public boolean contains(SurfaceAnchor support, BlockPosition target) {
        // A support identity is not a promise to center the actor on its block.
        // Cover the full block plus the declared body's half-width at its edge.
        // The native actuator keeps the original physical radius; no longer reach is granted.
        long dx = Math.abs(((long) target.x() - support.x()) * 1000) + horizontalSupportExtentMilli;
        long dz = Math.abs(((long) target.z() - support.z()) * 1000) + horizontalSupportExtentMilli;
        long dy = ((long) target.y() - support.y()) * 1000 + 500 - eyeAboveSupportMilli;
        if (Math.abs(dx) > radiusMilli || Math.abs(dy) > radiusMilli || Math.abs(dz) > radiusMilli) return false;
        return dx * dx + dy * dy + dz * dz <= (long) radiusMilli * radiusMilli;
    }
}

package io.farfrontier.palemirror.frontier.v3.model.geometry;

import io.farfrontier.palemirror.frontier.v3.model.BlockPosition;
import io.farfrontier.palemirror.frontier.v3.model.SurfaceAnchor;

/** Pure fixed-point reach envelope. Geometry, not a source recipe, target selector or actuator. */
public record BlockInteractionReach(int radiusMilli, int eyeAboveSupportMilli) {
    /** Current vanilla adult pedestrian physical envelope, not a work-speed or planning-radius tuning. */
    public static final BlockInteractionReach VANILLA_ADULT_PEDESTRIAN = new BlockInteractionReach(4500, 2620);
    public BlockInteractionReach {
        if (radiusMilli < 1 || radiusMilli > 6000 || eyeAboveSupportMilli < 1000 || eyeAboveSupportMilli > 4000)
            throw new IllegalArgumentException("invalid bounded block interaction reach");
    }
    public boolean contains(SurfaceAnchor support, BlockPosition target) {
        long dx = ((long) target.x() - support.x()) * 1000;
        long dz = ((long) target.z() - support.z()) * 1000;
        long dy = ((long) target.y() - support.y()) * 1000 + 500 - eyeAboveSupportMilli;
        if (Math.abs(dx) > radiusMilli || Math.abs(dy) > radiusMilli || Math.abs(dz) > radiusMilli) return false;
        return dx * dx + dy * dy + dz * dz <= (long) radiusMilli * radiusMilli;
    }
}

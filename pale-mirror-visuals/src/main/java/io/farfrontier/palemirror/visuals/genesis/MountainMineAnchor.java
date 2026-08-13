package io.farfrontier.palemirror.visuals.genesis;

import io.farfrontier.palemirror.api.VisualPoint;
import java.util.Objects;

/** Verified portal cut into a mountain face; direction points from the portal into solid terrain. */
public record MountainMineAnchor(VisualPoint portal, int inwardQuarterTurns) {
    public MountainMineAnchor {
        Objects.requireNonNull(portal, "portal");
        inwardQuarterTurns = Math.floorMod(inwardQuarterTurns, 4);
    }
}

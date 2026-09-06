package io.farfrontier.palemirror.visuals.genesis;

import io.farfrontier.palemirror.api.AuthoredMineRole;
import io.farfrontier.palemirror.api.VisualPoint;
import java.util.Map;
import java.util.Objects;

/** Verified portal cut into a mountain face; direction points from the portal into solid terrain. */
public record MountainMineAnchor(VisualPoint portal, int inwardQuarterTurns, Map<String, VisualPoint> surfaceCenters) {
    public MountainMineAnchor(VisualPoint portal, int inwardQuarterTurns) {
        this(portal, inwardQuarterTurns, Map.of());
    }

    public MountainMineAnchor {
        Objects.requireNonNull(portal, "portal");
        inwardQuarterTurns = Math.floorMod(inwardQuarterTurns, 4);
        surfaceCenters = Map.copyOf(surfaceCenters);
    }

    VisualPoint surfaceCenter(AuthoredMineRole role, String padId) {
        if (surfaceCenters.isEmpty()) {
            return MineSurfaceLayout.require(role, padId).center(portal, inwardQuarterTurns, portal.y() - 1);
        }
        VisualPoint value = surfaceCenters.get(padId);
        if (value == null) throw new IllegalStateException("Verified MineSite is missing surface pad " + padId);
        return value;
    }
}

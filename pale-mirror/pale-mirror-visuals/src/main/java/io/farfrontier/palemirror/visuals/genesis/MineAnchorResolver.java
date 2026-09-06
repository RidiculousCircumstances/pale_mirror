package io.farfrontier.palemirror.visuals.genesis;

import java.util.List;
import io.farfrontier.palemirror.api.VisualPoint;

/** Resolves one bounded deterministic mine-candidate list to a verified physical anchor. */
@FunctionalInterface
public interface MineAnchorResolver {
    MountainMineAnchor resolve(SitePlacementRequirement requirement, List<VisualPoint> candidates);
}

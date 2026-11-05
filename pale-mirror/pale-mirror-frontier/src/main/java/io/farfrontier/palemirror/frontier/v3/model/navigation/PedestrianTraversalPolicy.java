package io.farfrontier.palemirror.frontier.v3.model.navigation;

import io.farfrontier.palemirror.frontier.v3.model.BlockPosition;
import io.farfrontier.palemirror.frontier.v3.model.geometry.RegionalBlockSet;
import java.util.Set;

/** Soft spatial preferences, independent of collision, access and movement authority. */
public record PedestrianTraversalPolicy(RegionalBlockSet obstacles) {
    public static final PedestrianTraversalPolicy ORDINARY = new PedestrianTraversalPolicy(RegionalBlockSet.copyOf(Set.of()));
    public PedestrianTraversalPolicy { java.util.Objects.requireNonNull(obstacles); }
    public int penalty(BlockPosition support) { return obstacles.contains(support) ? 12 : 0; }
}

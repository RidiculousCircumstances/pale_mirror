package io.farfrontier.palemirror.frontier.v3.model.geometry;

import io.farfrontier.palemirror.frontier.v3.model.BlockPosition;
import java.util.Optional;

/** Read-only immutable facts for COLD/planners, or a bounded main-thread physical observation. */
@FunctionalInterface
public interface NavigationCollisionView {
    Optional<NavigationCell> cell(BlockPosition position);
    /** Full-cube-only providers may retain their cheaper existing clearance path. */
    default boolean detailedAt(int x, int z) { return true; }
}

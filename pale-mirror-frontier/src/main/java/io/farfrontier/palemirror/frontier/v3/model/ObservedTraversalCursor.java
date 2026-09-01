package io.farfrontier.palemirror.frontier.v3.model;

import java.util.List;
import java.util.Objects;

/**
 * Reconstructs a bounded traversal cursor solely from observed exact body arrival.
 *
 * <p>HOT adapters use this after a load or restart instead of retaining a private movement
 * index. A body outside every node starts at the declared first node; an observed later node may
 * advance only to its immediate successor.</p>
 */
public final class ObservedTraversalCursor {
    private ObservedTraversalCursor() { }

    public static int nextTargetIndex(BodyPosition bodyPosition, List<BodyPosition> targets, int arrivalDistanceSquared) {
        Objects.requireNonNull(bodyPosition, "observed body position");
        targets = List.copyOf(Objects.requireNonNull(targets, "traversal targets"));
        if (targets.isEmpty()) throw new IllegalArgumentException("traversal has no targets");
        if (arrivalDistanceSquared < 0) throw new IllegalArgumentException("arrival distance must not be negative");
        int arrived = -1;
        for (int index = 0; index < targets.size(); index++) {
            BodyPosition target = Objects.requireNonNull(targets.get(index), "traversal target");
            long deltaX = bodyPosition.x() - target.x(), deltaY = bodyPosition.y() - target.y(), deltaZ = bodyPosition.z() - target.z();
            if (deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ <= arrivalDistanceSquared) arrived = index;
        }
        return arrived < 0 ? 0 : Math.min(arrived + 1, targets.size() - 1);
    }
}

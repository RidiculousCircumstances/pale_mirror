package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.BlockPosition;
import io.farfrontier.palemirror.frontier.v3.model.LocalNavigationEnvelope;
import io.farfrontier.palemirror.frontier.v3.model.WorldBounds;

import java.util.Objects;

/** Hard task restrictions, never a stripe guessed from a suggested path. */
sealed interface FrontierV3NavigationScope {
    boolean permits(BlockPosition support);

    /** Explicit topology/formation restrictions remain binding for their existing owners. */
    record Restricted(LocalNavigationEnvelope envelope) implements FrontierV3NavigationScope {
        public Restricted { Objects.requireNonNull(envelope, "restricted navigation scope"); }
        @Override public boolean permits(BlockPosition support) { return envelope.contains(support); }
    }

    /** A retained owner approach keeps hard latitude without materializing an unbounded envelope. */
    record RetainedApproach(java.util.List<io.farfrontier.palemirror.frontier.v3.model.SurfaceAnchor> path)
            implements FrontierV3NavigationScope {
        public RetainedApproach {
            path = java.util.List.copyOf(Objects.requireNonNull(path));
            if (path.isEmpty() || path.size() > io.farfrontier.palemirror.frontier.v3.model.navigation.TraversalRejoin.MAX_SURFACES)
                throw new IllegalArgumentException("retained approach exceeds bounded navigation scope");
        }
        @Override public boolean permits(BlockPosition support) {
            return path.stream().anyMatch(surface -> Math.abs((long) surface.x() - support.x()) <= 1
                    && Math.abs((long) surface.y() - support.y()) <= 1 && Math.abs((long) surface.z() - support.z()) <= 1);
        }
    }

    /** Physical evidence may authorize a bounded HOT detour, not an unloaded-world query. */
    record ObservedWorld(WorldBounds bounds) implements FrontierV3NavigationScope {
        public ObservedWorld { Objects.requireNonNull(bounds, "navigation world bounds"); }
        @Override public boolean permits(BlockPosition support) { return bounds.contains(support); }
    }
}

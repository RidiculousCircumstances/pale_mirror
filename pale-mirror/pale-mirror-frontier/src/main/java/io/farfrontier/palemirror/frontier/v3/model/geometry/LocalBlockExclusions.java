package io.farfrontier.palemirror.frontier.v3.model.geometry;

import io.farfrontier.palemirror.frontier.v3.model.BlockPosition;
import java.util.*;

/** Disposable owner-thread projection of revoked facts onto one immutable query footprint. */
public final class LocalBlockExclusions {
    private Set<BlockPosition> priorFootprint;
    private KnownBlockExclusions priorExclusions;
    private Set<BlockPosition> relevant = Set.of();

    public Set<BlockPosition> read(Set<BlockPosition> footprint, KnownBlockExclusions exclusions) {
        Objects.requireNonNull(footprint); Objects.requireNonNull(exclusions);
        if (priorFootprint != footprint || priorExclusions != exclusions) {
            boolean unchanged = priorFootprint == footprint && priorExclusions != null
                    && exclusions.additionsSince(priorExclusions)
                        .filter(added -> Collections.disjoint(added, footprint)).isPresent();
            if (!unchanged) {
                var next = new HashSet<BlockPosition>();
                for (var position : footprint) if (exclusions.positions().contains(position)) next.add(position);
                if (!next.equals(relevant)) relevant = Set.copyOf(next);
            }
            priorFootprint = footprint; priorExclusions = exclusions;
        }
        return relevant;
    }
}

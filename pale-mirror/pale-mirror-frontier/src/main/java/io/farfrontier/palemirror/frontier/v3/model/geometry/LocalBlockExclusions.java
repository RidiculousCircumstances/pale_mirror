package io.farfrontier.palemirror.frontier.v3.model.geometry;

import io.farfrontier.palemirror.frontier.v3.model.BlockPosition;
import java.util.*;

/** Disposable owner-thread projection of revoked facts onto one immutable query footprint. */
public final class LocalBlockExclusions {
    private Set<BlockPosition> priorFootprint;
    private KnownBlockExclusions priorExclusions;
    private RegionalBlockSet indexedFootprint = RegionalBlockSet.copyOf(Set.of());
    private RegionalBlockSet relevant = RegionalBlockSet.copyOf(Set.of());

    public Set<BlockPosition> read(Set<BlockPosition> footprint, KnownBlockExclusions exclusions) {
        Objects.requireNonNull(footprint); Objects.requireNonNull(exclusions);
        if (priorFootprint != footprint || priorExclusions != exclusions) {
            var nextFootprint = priorFootprint == footprint ? indexedFootprint : RegionalBlockSet.copyOf(footprint);
            var revoked = RegionalBlockSet.copyOf(exclusions.positions());
            var changed = new HashSet<>(nextFootprint.changesFrom(indexedFootprint));
            changed.addAll(revoked.changesFrom(priorExclusions == null ? RegionalBlockSet.copyOf(Set.of())
                    : RegionalBlockSet.copyOf(priorExclusions.positions())));
            var updates = new HashMap<io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianGeometryRevision.Region, Set<BlockPosition>>();
            for (var region : changed) {
                var queries = nextFootprint.in(region); var removed = revoked.in(region);
                var next = new HashSet<BlockPosition>();
                var smaller = queries.size() <= removed.size() ? queries : removed;
                var larger = queries.size() <= removed.size() ? removed : queries;
                for (var position : smaller) if (larger.contains(position)) next.add(position);
                updates.put(region, next);
            }
            relevant = relevant.replace(updates); indexedFootprint = nextFootprint;
            priorFootprint = footprint; priorExclusions = exclusions;
        }
        return relevant;
    }
}

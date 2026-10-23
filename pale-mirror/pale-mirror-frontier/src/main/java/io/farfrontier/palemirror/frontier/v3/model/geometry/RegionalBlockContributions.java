package io.farfrontier.palemirror.frontier.v3.model.geometry;

import io.farfrontier.palemirror.frontier.v3.model.BlockPosition;
import io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianGeometryRevision.Region;
import java.util.*;

/** Owner-thread composition of immutable producer snapshots. Overlapping declarations remain independent. */
public final class RegionalBlockContributions<K> {
    private final Map<K, RegionalBlockSet> sources = new HashMap<>();
    private RegionalBlockSet snapshot = RegionalBlockSet.copyOf(Set.of());

    /** Each producer supplies its exact indexed contribution; only affected regional unions are rebuilt. */
    public RegionalBlockContributions() { }
    private RegionalBlockContributions(RegionalBlockContributions<K> source) { sources.putAll(source.sources); snapshot = source.snapshot; }
    public RegionalBlockContributions<K> fork() { return new RegionalBlockContributions<>(this); }

    public RegionalBlockSet replace(K owner, RegionalBlockSet contribution) {
        Objects.requireNonNull(owner); Objects.requireNonNull(contribution);
        var prior = sources.getOrDefault(owner, RegionalBlockSet.copyOf(Set.of()));
        var changed = contribution.changesFrom(prior);
        if (contribution.isEmpty()) sources.remove(owner); else sources.put(owner, contribution);
        var updates = new HashMap<Region, Set<BlockPosition>>();
        for (var region : changed) {
            var blocks = new HashSet<BlockPosition>();
            for (var source : sources.values()) blocks.addAll(source.in(region));
            updates.put(region, blocks);
        }
        snapshot = snapshot.replace(updates); return snapshot;
    }
    public RegionalBlockSet snapshot() { return snapshot; }
}

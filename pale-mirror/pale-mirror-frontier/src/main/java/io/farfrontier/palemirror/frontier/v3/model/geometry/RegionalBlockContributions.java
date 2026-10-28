package io.farfrontier.palemirror.frontier.v3.model.geometry;

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
        return replaceAll(Map.of(owner, contribution));
    }
    /** One input epoch publishes one final union, never intermediate world-wide unions. */
    public RegionalBlockSet replaceAll(Map<K, RegionalBlockSet> contributions) {
        var changed = new HashSet<Region>();
        contributions.forEach((owner, contribution) -> {
            Objects.requireNonNull(owner); Objects.requireNonNull(contribution);
            changed.addAll(contribution.changesFrom(sources.getOrDefault(owner, RegionalBlockSet.copyOf(Set.of()))));
        });
        contributions.forEach((owner, contribution) -> {
            if (contribution.isEmpty()) sources.remove(owner); else sources.put(owner, contribution);
        });
        snapshot = snapshot.replaceUnion(changed, sources.values());
        return snapshot;
    }
    public RegionalBlockSet snapshot() { return snapshot; }
}

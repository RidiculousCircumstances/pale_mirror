package io.farfrontier.palemirror.frontier.v3.model.geometry;

import io.farfrontier.palemirror.frontier.v3.model.BlockPosition;
import io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianGeometryRevision.Region;
import java.util.*;

/** Owner-thread composition of exact facts. Only changed regions are joined; disagreement fails before publication. */
public final class RegionalBlockMapContributions<K, V> {
    private final Map<K, RegionalBlockMap<V>> sources = new HashMap<>();
    private RegionalBlockMap<V> snapshot = RegionalBlockMap.copyOf(Map.of());

    public RegionalBlockMap<V> replace(K owner, RegionalBlockMap<V> contribution) {
        Objects.requireNonNull(owner); Objects.requireNonNull(contribution);
        var prior = sources.getOrDefault(owner, RegionalBlockMap.copyOf(Map.of()));
        var changed = contribution.changesFrom(prior);
        var updates = new HashMap<Region, Map<BlockPosition, V>>();
        for (var region : changed) {
            var facts = new HashMap<BlockPosition, V>(contribution.in(region));
            for (var entry : sources.entrySet()) if (!entry.getKey().equals(owner))
                entry.getValue().in(region).forEach((position, value) -> {
                    var previous = facts.putIfAbsent(position, value);
                    if (previous != null && !previous.equals(value))
                        throw new IllegalArgumentException("competing geometry declarations at " + position);
                });
            updates.put(region, facts);
        }
        var next = snapshot.replace(updates);
        if (contribution.isEmpty()) sources.remove(owner); else sources.put(owner, contribution);
        snapshot = next;
        return snapshot;
    }
    public RegionalBlockMap<V> snapshot() { return snapshot; }
}

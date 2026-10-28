package io.farfrontier.palemirror.frontier.v3.model;

import java.util.*;

/** Computing-thread union of explicitly supplied immutable contributions.
 * Removing one contributor cannot remove a value still declared by another.
 * Snapshots never expose mutable counts or become an ownership authority. */
public final class ImmutableSetContributions<K, V> {
    private final int maximumContributors;
    private final Map<K, Set<V>> sources = new HashMap<>();
    private final Map<V, Integer> counts = new HashMap<>();
    private Set<V> snapshot = Set.of();
    private boolean failed;

    public ImmutableSetContributions(int maximumContributors) {
        if (maximumContributors < 1) throw new IllegalArgumentException("positive contribution bound required");
        this.maximumContributors = maximumContributors;
    }
    public Set<V> replaceAll(Map<K, Set<V>> changes) {
        requireHealthy();
        var prepared = new HashMap<K, Set<V>>(); int retained = sources.size();
        for (var entry : changes.entrySet()) {
            var key = Objects.requireNonNull(entry.getKey()); var values = Set.copyOf(entry.getValue());
            prepared.put(key, values);
            if (!sources.containsKey(key) && !values.isEmpty()) retained++;
            else if (sources.containsKey(key) && values.isEmpty()) retained--;
        }
        if (retained > maximumContributors) throw new IllegalArgumentException("contribution scope exceeds its producer bound");
        try {
            boolean membershipChanged = false;
            for (var entry : prepared.entrySet()) {
                var before = sources.getOrDefault(entry.getKey(), Set.of()); var after = entry.getValue();
                if (before.equals(after)) continue;
                for (var value : before) if (!after.contains(value)) {
                    int remaining = counts.get(value) - 1;
                    if (remaining == 0) { counts.remove(value); membershipChanged = true; }
                    else counts.put(value, remaining);
                }
                for (var value : after) if (!before.contains(value)) {
                    int next = Math.addExact(counts.getOrDefault(value, 0), 1);
                    counts.put(value, next); if (next == 1) membershipChanged = true;
                }
                if (after.isEmpty()) sources.remove(entry.getKey()); else sources.put(entry.getKey(), after);
            }
            if (membershipChanged && !snapshot.equals(counts.keySet())) snapshot = ImmutableLookupSet.copyOf(counts.keySet());
            return snapshot;
        } catch (RuntimeException | Error invalid) { failed = true; throw invalid; }
    }
    public Set<V> snapshot() { requireHealthy(); return snapshot; }
    private void requireHealthy() { if (failed) throw new IllegalStateException("failed contribution image must be discarded"); }
}

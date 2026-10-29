package io.farfrontier.palemirror.frontier.v3.model;

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.*;
import java.util.function.Supplier;

/** Completed immutable results shared by preparation and the committing owner.
 * Bounded weak snapshot identities do not retain otherwise-dead world versions.
 * Mutable working maps and allocations must remain calculation-local. A miss or
 * eviction always recomputes; a matching snapshot grants no authority. */
public final class ImmutableSnapshotView<K, V> {
    private static final class Snapshot extends WeakReference<Object> {
        private final int hash;
        Snapshot(Object value, ReferenceQueue<Object> retired) { super(value, retired); hash = System.identityHashCode(value); }
        @Override public int hashCode() { return hash; }
        @Override public boolean equals(Object other) {
            if (this == other) return true;
            var value = get(); return value != null && other instanceof Snapshot snapshot && value == snapshot.get();
        }
    }
    private final Map<Snapshot, ImmutableResultView<K, V>> snapshots = new LinkedHashMap<>(4, .75f, true);
    private final ReferenceQueue<Object> retired = new ReferenceQueue<>();
    private final int maximumSnapshots, queriesPerSnapshot;
    public ImmutableSnapshotView(int maximumSnapshots, int queriesPerSnapshot) {
        if (maximumSnapshots < 1 || queriesPerSnapshot < 1) throw new IllegalArgumentException("positive snapshot/query bounds required");
        this.maximumSnapshots = maximumSnapshots; this.queriesPerSnapshot = queriesPerSnapshot;
    }
    public V get(Object immutableSnapshot, K query, Supplier<V> compute) {
        Objects.requireNonNull(immutableSnapshot); Objects.requireNonNull(query); Objects.requireNonNull(compute);
        try {
            ImmutableResultView<K, V> results;
            synchronized (snapshots) {
                Reference<?> gone;
                while ((gone = retired.poll()) != null) snapshots.remove(gone);
                results = snapshots.get(new Snapshot(immutableSnapshot, null));
                if (results == null) {
                    if (snapshots.size() >= maximumSnapshots) snapshots.remove(snapshots.keySet().iterator().next());
                    results = new ImmutableResultView<>(queriesPerSnapshot);
                    snapshots.put(new Snapshot(immutableSnapshot, retired), results);
                }
            }
            // Domain calculation never runs under the shared snapshot-cache lock.
            return results.get(List.of(), query, compute);
        } finally {
            Reference.reachabilityFence(immutableSnapshot);
        }
    }
}

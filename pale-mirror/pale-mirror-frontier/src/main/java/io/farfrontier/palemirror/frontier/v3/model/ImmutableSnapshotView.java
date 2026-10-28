package io.farfrontier.palemirror.frontier.v3.model;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/** Completed immutable results shared by preparation and the committing owner.
 * Retains only a bounded number of exact immutable snapshots and query results.
 * Mutable working maps and allocations must remain calculation-local. A miss or
 * eviction always recomputes; neither a query nor a snapshot match grants authority. */
public final class ImmutableSnapshotView<K, V> {
    private final ImmutableResultView<Boolean, ImmutableResultView<K, V>> snapshots;
    private final int queriesPerSnapshot;
    public ImmutableSnapshotView(int maximumSnapshots, int queriesPerSnapshot) {
        if (queriesPerSnapshot < 1) throw new IllegalArgumentException("positive query bound required");
        snapshots = new ImmutableResultView<>(maximumSnapshots);
        this.queriesPerSnapshot = queriesPerSnapshot;
    }
    public V get(Object immutableSnapshot, K query, Supplier<V> compute) {
        Objects.requireNonNull(immutableSnapshot); Objects.requireNonNull(query); Objects.requireNonNull(compute);
        var results = snapshots.get(List.of(immutableSnapshot), Boolean.TRUE,
                () -> new ImmutableResultView<K, V>(queriesPerSnapshot));
        return results.get(List.of(), query, compute);
    }
}

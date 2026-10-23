package io.farfrontier.palemirror.frontier.v3.model;

import java.util.*;
import java.util.function.Supplier;

/** Shares completed immutable results between preparation and reduction. Mutable working
 * maps must use ImmutableInputView instead. Exact input identities, no TTL or authority. */
public final class ImmutableResultView<K, V> {
    private final BoundedValueMemo<Key<K>, V> results;
    public ImmutableResultView(int capacity) { results = new BoundedValueMemo<>(capacity); }
    public V get(List<Object> inputs, K query, Supplier<V> compute) {
        return results.get(new Key<>(inputs, query), compute);
    }
    private record Key<K>(List<Object> inputs, K query) {
        private Key { inputs = List.copyOf(inputs); Objects.requireNonNull(query); }
        @Override public boolean equals(Object other) {
            return other instanceof Key<?> key && query.equals(key.query) && ImmutablePlanningScope.same(inputs, key.inputs);
        }
        @Override public int hashCode() {
            int result = query.hashCode();
            for (var input : inputs) result = 31 * result + System.identityHashCode(input);
            return result;
        }
    }
}

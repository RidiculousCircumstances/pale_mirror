package io.farfrontier.palemirror.frontier.v3.model.geometry;

import java.lang.ref.WeakReference;
import java.util.Objects;

/** Exact equality of defensively owned immutable facts, with one weak proven peer. */
abstract class ImmutableRegionalValue<T> {
    final T values;
    final int hash;
    private volatile WeakReference<ImmutableRegionalValue<?>> equalPeer;

    ImmutableRegionalValue(T values) {
        this.values = Objects.requireNonNull(values); hash = values.hashCode();
    }
    @Override public final int hashCode() { return hash; }
    @Override public final boolean equals(Object other) {
        if (this == other) return true;
        if (other == null || getClass() != other.getClass()) return false;
        var value = (ImmutableRegionalValue<?>) other;
        if (hash != value.hash) return false;
        var known = equalPeer;
        if (known != null && known.get() == value) return true;
        if (!values.equals(value.values)) return false;
        equalPeer = new WeakReference<>(value);
        value.equalPeer = new WeakReference<>(this);
        return true;
    }
}

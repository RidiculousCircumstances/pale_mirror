package io.farfrontier.palemirror.frontier.v3.model;

import java.util.AbstractList;
import java.util.List;
import java.util.Objects;
import java.util.RandomAccess;
import java.util.function.Supplier;

/** Immutable derived values computed only on demand. The supplier must capture immutable inputs.
 * Each list owns its initialization lock; unrelated regions never wait on a world-wide monitor. */
public final class DeferredImmutableList<E> extends AbstractList<E> implements RandomAccess {
    private volatile List<E> values;
    private Supplier<List<E>> source;
    public DeferredImmutableList(Supplier<List<E>> source) { this.source = Objects.requireNonNull(source); }
    private List<E> values() {
        var ready = values;
        if (ready != null) return ready;
        synchronized (this) {
            if (values == null) { values = List.copyOf(source.get()); source = null; }
            return values;
        }
    }
    @Override public E get(int index) { return values().get(index); }
    @Override public int size() { return values().size(); }
}

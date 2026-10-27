package io.farfrontier.palemirror.frontier.v3.model;

import java.util.*;
import java.util.function.Predicate;

/** Defensive immutable membership with hashed lookup, including clustered domain
 * identifiers. Preserves source iteration order; it grants no type or ownership. */
public final class ImmutableLookupSet<E> extends AbstractSet<E> {
    private final Set<E> values;
    private ImmutableLookupSet(Collection<? extends E> source) {
        var copy = new LinkedHashSet<E>(); source.forEach(value -> copy.add(Objects.requireNonNull(value)));
        values = Collections.unmodifiableSet(copy);
    }
    public static <E> Set<E> copyOf(Collection<? extends E> source) {
        Objects.requireNonNull(source);
        if (source instanceof ImmutableLookupSet<?>) {
            @SuppressWarnings("unchecked") Set<E> retained = (Set<E>) source;
            return retained;
        }
        return new ImmutableLookupSet<>(source);
    }
    @Override public Iterator<E> iterator() { return values.iterator(); }
    @Override public int size() { return values.size(); }
    @Override public boolean contains(Object value) { return values.contains(Objects.requireNonNull(value)); }
    @Override public boolean add(E value) { return values.add(value); }
    @Override public boolean remove(Object value) { return values.remove(value); }
    @Override public boolean addAll(Collection<? extends E> values) { return this.values.addAll(values); }
    @Override public boolean removeAll(Collection<?> values) { return this.values.removeAll(values); }
    @Override public boolean retainAll(Collection<?> values) { return this.values.retainAll(values); }
    @Override public boolean removeIf(Predicate<? super E> filter) { return values.removeIf(filter); }
    @Override public void clear() { values.clear(); }
}

package io.farfrontier.palemirror.frontier.v3.model;

import java.util.*;
import java.util.function.BiConsumer;

/** Successful checks whose complete read set is one immutable key/value pair.
 * Each instance has one owner's fixed algorithm. Foreign dependencies belong to
 * ImmutableInvariantRegistry instead. Full ingress audits always visit all rows. */
final class ImmutableEntryInvariantCheck<K, V> {
    private record Image<K, V>(Object epoch, Map<K, V> source) { }
    private final int maximumEntries;
    private final ThreadLocal<Image<K, V>> current = new ThreadLocal<>();
    ImmutableEntryInvariantCheck(int maximumEntries) {
        if (maximumEntries < 1) throw new IllegalArgumentException("entry check needs a declared positive bound");
        this.maximumEntries = maximumEntries;
    }
    void validate(Object epoch, Map<K, V> registry, BiConsumer<K, V> check) {
        Objects.requireNonNull(epoch); Objects.requireNonNull(registry); Objects.requireNonNull(check);
        if (registry.size() > maximumEntries) throw new IllegalArgumentException("entry check exceeds its producer bound");
        var source = registry instanceof ImmutableMapImage<?, ?> ? registry : ImmutableLookupMap.copyOf(registry);
        var prior = current.get();
        try {
            if (ImmutableInvariantCheck.fullAudit() || prior == null || prior.epoch() != epoch) source.forEach(check);
            else ImmutableMapChanges.forEachIdentityChangedKey(prior.source(), source, key -> {
                var row = source.get(key);
                if (row != null) check.accept(key, row);
            });
            current.set(new Image<>(epoch, source));
        } catch (RuntimeException | Error failed) { current.remove(); throw failed; }
    }
}

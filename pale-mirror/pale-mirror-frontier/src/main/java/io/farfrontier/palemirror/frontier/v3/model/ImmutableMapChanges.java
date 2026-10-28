package io.farfrontier.palemirror.frontier.v3.model;

import java.util.*;
import java.util.function.Consumer;

/** Exact changes between immutable component images; no domain dispatch or mutation. */
final class ImmutableMapChanges {
    record Change<K, V>(K key, V before, V after) { }
    private ImmutableMapChanges() { }
    static <K, V> void forEach(Map<K, V> before, Map<K, V> after, Consumer<Change<K, V>> consume) {
        if (before == after) return;
        for (var entry : before.entrySet()) {
            var next = after.get(entry.getKey());
            if (!Objects.equals(entry.getValue(), next)) consume.accept(new Change<>(entry.getKey(), entry.getValue(), next));
        }
        for (var entry : after.entrySet()) if (!before.containsKey(entry.getKey()))
            consume.accept(new Change<>(entry.getKey(), null, entry.getValue()));
    }
}

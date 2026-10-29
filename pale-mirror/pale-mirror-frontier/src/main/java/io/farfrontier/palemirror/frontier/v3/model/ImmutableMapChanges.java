package io.farfrontier.palemirror.frontier.v3.model;

import java.util.*;
import java.util.function.Consumer;

/** Exact changes between immutable component images; no domain dispatch or mutation. */
final class ImmutableMapChanges {
    record Change<K, V>(K key, V before, V after) { }
    private ImmutableMapChanges() { }
    static boolean sameKeys(Map<?, ?> before, Map<?, ?> after) {
        if (before == after) return true;
        if (before.size() != after.size()) return false;
        var changed = ImmutableMapDifferences.untyped(before, after);
        if (changed.isPresent()) {
            for (var key : changed.orElseThrow()) if (before.containsKey(key) != after.containsKey(key)) return false;
            return true;
        }
        return before.keySet().equals(after.keySet());
    }
    /** Complete identity changes for immutable dependency evidence, not value equality. */
    static <K> void forEachIdentityChangedKey(Map<K, ?> before, Map<K, ?> after, Consumer<K> consume) {
        if (before == after) return;
        var keys = ImmutableMapDifferences.keys(before, after);
        if (keys.isPresent()) {
            for (var key : keys.orElseThrow()) if (before.get(key) != after.get(key)) consume.accept(key);
            return;
        }
        for (var key : before.keySet()) if (before.get(key) != after.get(key)) consume.accept(key);
        for (var key : after.keySet()) if (!before.containsKey(key)) consume.accept(key);
    }
    /** Membership-only removal delta: value changes must not compare large records. */
    static <K> void forEachRemovedKey(Map<K, ?> before, Map<K, ?> after, Consumer<K> consume) {
        if (before == after) return;
        var keys = ImmutableMapDifferences.keys(before, after);
        if (keys.isPresent()) {
            for (var key : keys.orElseThrow()) if (before.containsKey(key) && !after.containsKey(key)) consume.accept(key);
            return;
        }
        for (var key : before.keySet()) if (!after.containsKey(key)) consume.accept(key);
    }
    static <K, V> void forEach(Map<K, V> before, Map<K, V> after, Consumer<Change<K, V>> consume) {
        if (before == after) return;
        var keys = ImmutableMapDifferences.keys(before, after);
        if (keys.isPresent()) {
            for (var key : keys.orElseThrow()) {
                var previous = before.get(key); var next = after.get(key);
                if (!Objects.equals(previous, next)) consume.accept(new Change<>(key, previous, next));
            }
            return;
        }
        for (var entry : before.entrySet()) {
            var next = after.get(entry.getKey());
            if (!Objects.equals(entry.getValue(), next)) consume.accept(new Change<>(entry.getKey(), entry.getValue(), next));
        }
        for (var entry : after.entrySet()) if (!before.containsKey(entry.getKey()))
            consume.accept(new Change<>(entry.getKey(), null, entry.getValue()));
    }
}

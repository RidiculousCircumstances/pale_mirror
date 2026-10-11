package io.farfrontier.palemirror.frontier.v3.model;

import java.util.*;

/** Frozen placement policy. Named categories carry explicit membership, never item-name inference. */
public record ResourceStorageFilter(boolean allItems, Set<String> items, Map<String, Set<String>> categories) {
    public ResourceStorageFilter {
        items = Set.copyOf(Objects.requireNonNull(items));
        var frozen = new TreeMap<String, Set<String>>();
        Objects.requireNonNull(categories).forEach((name, members) -> {
            requireKey(name);
            var copy = Set.copyOf(Objects.requireNonNull(members));
            if (copy.isEmpty() || copy.size() > 4096) throw new IllegalArgumentException("storage category needs bounded membership");
            copy.forEach(ResourceStorageFilter::requireKey); frozen.put(name, copy);
        });
        categories = Map.copyOf(frozen);
        if (items.size() > 4096 || categories.size() > 256
                || (long) items.size() + categories.values().stream().mapToLong(Set::size).sum() > 4096
                || allItems && (!items.isEmpty() || !categories.isEmpty()))
            throw new IllegalArgumentException("invalid bounded storage filter");
        items.forEach(ResourceStorageFilter::requireKey);
    }
    public static ResourceStorageFilter all() { return new ResourceStorageFilter(true, Set.of(), Map.of()); }
    public boolean accepts(String itemKind) {
        requireKey(itemKind);
        return allItems || items.contains(itemKind) || categories.values().stream().anyMatch(values -> values.contains(itemKind));
    }
    private static void requireKey(String key) {
        if (key == null || !key.matches("[a-z][a-z0-9_-]{0,31}:[a-z0-9][a-z0-9_./-]{0,127}"))
            throw new IllegalArgumentException("storage filter requires a namespaced resource/category key");
    }
}

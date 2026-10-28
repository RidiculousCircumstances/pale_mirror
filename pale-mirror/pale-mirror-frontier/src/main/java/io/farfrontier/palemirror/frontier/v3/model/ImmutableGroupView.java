package io.farfrontier.palemirror.frontier.v3.model;

import java.util.*;
import java.util.function.Function;

/** Disposable computing-thread inverse of explicitly declared row memberships.
 * Retains only the current producer registry, its memberships and queried lists
 * for actual groups. Unknown queries cannot grow it. Optional row images always
 * follow changed producer rows; no domain eligibility or authority is cached. */
final class ImmutableGroupView<K, V, G> {
    private static final class Image<K, V, G> {
        final Object epoch;
        Map<K, V> source = Map.of();
        final Map<K, Set<G>> memberships = new HashMap<>();
        final Map<G, Set<K>> groups = new HashMap<>();
        final Set<G> duplicates = new HashSet<>();
        final Map<G, List<K>> ordered = new HashMap<>();
        final Map<G, List<V>> rows = new HashMap<>();
        List<K> order = List.of();
        Map<K, Integer> ranks = Map.of();
        Image(Object epoch) { this.epoch = epoch; }
    }
    private final ThreadLocal<Image<K, V, G>> current = new ThreadLocal<>();
    private final ImmutableKeyOrderView<K> order = new ImmutableKeyOrderView<>(Integer.MAX_VALUE);

    List<K> keys(Object epoch, Map<K, V> source, Function<V, Set<G>> memberships, G group) {
        Objects.requireNonNull(epoch); Objects.requireNonNull(source);
        Objects.requireNonNull(memberships); Objects.requireNonNull(group);
        // Mutable ingress cannot become a trusted predecessor by object identity.
        Map<K, V> snapshot = source instanceof ImmutableMapImage<?, ?> ? source : ImmutableLookupMap.copyOf(source);
        var image = current.get();
        if (image == null || image.epoch != epoch) image = new Image<>(epoch);
        try {
            update(image, snapshot, memberships);
            current.set(image);
            var candidates = image.groups.get(group);
            if (candidates == null) return List.of();
            var retained = image.ordered.get(group);
            if (retained != null) return retained;
            var sorted = new ArrayList<>(candidates);
            var ranks = image.ranks;
            sorted.sort(Comparator.comparingInt(ranks::get));
            var result = List.copyOf(sorted);
            image.ordered.put(group, result);
            return result;
        } catch (RuntimeException | Error failed) {
            current.remove();
            throw failed;
        }
    }

    /** Current row values in producer order, shared until that group's rows or
     * order change. Unknown groups retain nothing; no predecessor image is kept. */
    List<V> rows(Object epoch, Map<K, V> source, Function<V, Set<G>> memberships, G group) {
        var keys = keys(epoch, source, memberships, group);
        if (keys.isEmpty()) return List.of();
        var image = current.get();
        return image.rows.computeIfAbsent(group, ignored -> keys.stream()
                .map(key -> Objects.requireNonNull(image.source.get(key), "group lost its current row")).toList());
    }

    /** Uniqueness of producer-declared memberships, never discovery of canonical ownership. */
    boolean uniqueMemberships(Object epoch, Map<K, V> source, Function<V, Set<G>> memberships) {
        Objects.requireNonNull(epoch); Objects.requireNonNull(source); Objects.requireNonNull(memberships);
        Map<K, V> snapshot = source instanceof ImmutableMapImage<?, ?> ? source : ImmutableLookupMap.copyOf(source);
        var image = current.get();
        if (image == null || image.epoch != epoch || ImmutableInvariantCheck.fullAudit()) image = new Image<>(epoch);
        try {
            update(image, snapshot, memberships); current.set(image);
            return image.duplicates.isEmpty();
        } catch (RuntimeException | Error failed) { current.remove(); throw failed; }
    }

    private void update(Image<K, V, G> image, Map<K, V> source, Function<V, Set<G>> project) {
        if (image.source == source) return;
        var nextOrder = order.get(source);
        if (nextOrder != image.order) {
            var ranks = new HashMap<K, Integer>();
            for (int i = 0; i < nextOrder.size(); i++) ranks.put(nextOrder.get(i), i);
            image.ranks = ranks; image.order = nextOrder; image.ordered.clear(); image.rows.clear();
        }
        ImmutableMapChanges.forEachIdentityChangedKey(image.source, source, key -> {
            var row = source.get(key);
            var next = row == null ? Set.<G>of() : Set.copyOf(project.apply(row));
            var previous = image.memberships.getOrDefault(key, Set.of());
            // A value replacement preserves candidate keys, but its old row
            // image must not survive even when membership stays unchanged.
            for (var group : previous) image.rows.remove(group);
            for (var group : next) image.rows.remove(group);
            if (previous.equals(next)) return;
            for (var group : previous) if (!next.contains(group)) {
                var keys = image.groups.get(group); keys.remove(key);
                if (keys.isEmpty()) image.groups.remove(group);
                if (keys.size() < 2) image.duplicates.remove(group);
                image.ordered.remove(group);
            }
            for (var group : next) if (!previous.contains(group)) {
                var keys = image.groups.computeIfAbsent(group, ignored -> new HashSet<>()); keys.add(key);
                if (keys.size() > 1) image.duplicates.add(group);
                image.ordered.remove(group);
            }
            if (next.isEmpty()) image.memberships.remove(key);
            else image.memberships.put(key, next);
        });
        image.source = source;
    }
}

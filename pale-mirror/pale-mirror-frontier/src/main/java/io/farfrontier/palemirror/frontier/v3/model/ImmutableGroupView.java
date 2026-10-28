package io.farfrontier.palemirror.frontier.v3.model;

import java.util.*;
import java.util.function.Function;

/** Disposable computing-thread inverse of explicitly declared row memberships.
 * Retains only the current producer registry, its memberships and queried lists
 * for actual groups. Unknown queries cannot grow it. No domain eligibility or
 * row values are cached; callers resolve candidates from their current source. */
final class ImmutableGroupView<K, V, G> {
    private static final class Image<K, V, G> {
        final Object epoch;
        Map<K, V> source = Map.of();
        final Map<K, Set<G>> memberships = new HashMap<>();
        final Map<G, Set<K>> groups = new HashMap<>();
        final Set<G> duplicates = new HashSet<>();
        final Map<G, List<K>> ordered = new HashMap<>();
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
            image.ranks = ranks; image.order = nextOrder; image.ordered.clear();
        }
        ImmutableMapChanges.forEachIdentityChangedKey(image.source, source, key -> {
            var row = source.get(key);
            var next = row == null ? Set.<G>of() : Set.copyOf(project.apply(row));
            var previous = image.memberships.getOrDefault(key, Set.of());
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

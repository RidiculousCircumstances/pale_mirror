package io.farfrontier.palemirror.frontier.v3.model;

import java.util.*;
import java.util.function.BiFunction;

/** Completed immutable owner facts over explicit reads, including absent addresses.
 * Reuses the reference index's changed-reader selection. No task, subscription,
 * timing, canonical mutation or admission authority. One current image per thread
 * and one shared completed image. Active decision branches reuse complete inputs
 * through the existing bounded lexical planning scope, released on its exit. */
public final class ImmutableReadProjection<C, K, D, V> {
    private static final class Image<K, D, V> {
        final Object epoch, generation = new Object();
        Map<K, D> declarations = Map.of();
        Map<K, V> values = ImmutableLookupMap.copyOf(Map.of());
        final Map<K, V> changes = new HashMap<>();
        Image(Object epoch) { this.epoch = epoch; }
    }
    private record Published<K, D, V>(Object epoch, Map<K, D> declarations,
                                     List<Map<?, ?>> roots, List<Object> basis, Map<K, V> values) { }
    private final List<ImmutableReferenceIndex.Source<C, ?, ?>> sources;
    private final ImmutableReferenceIndex<C, K, D> references;
    private final ThreadLocal<Image<K, D, V>> current = new ThreadLocal<>();
    private volatile Published<K, D, V> published;

    public ImmutableReadProjection(int maximumRows, int maximumReads,
            List<ImmutableReferenceIndex.Source<C, ?, ?>> sources,
            BiFunction<C, D, List<ImmutableReferenceIndex.Read<C>>> reads,
            BiFunction<C, D, V> compute) {
        this.sources = List.copyOf(sources);
        references = new ImmutableReferenceIndex<>(maximumRows, maximumReads, sources, reads, (context, key, row) ->
                current.get().changes.put(key, Objects.requireNonNull(compute.apply(context, row))));
    }
    public Map<K, V> project(Object epoch, Map<K, D> declarations, C context) {
        Objects.requireNonNull(epoch); Objects.requireNonNull(declarations); Objects.requireNonNull(context);
        try {
            var ready = published;
            var inputs = new ImmutableReferenceIndex.Inputs<>(sources, context, ready == null ? null : ready.roots());
            if (ImmutableInvariantCheck.fullAudit() || !ImmutablePlanningScope.active())
                return projectCurrent(epoch, declarations, context, inputs, null);
            var basis = matches(ready, epoch, declarations, inputs.roots) ? ready.basis() : basis(epoch, declarations, inputs.roots);
            return ImmutablePlanningScope.get(this, basis, () -> projectCurrent(epoch, declarations, context, inputs, basis));
        }
        catch (RuntimeException | Error failed) { current.remove(); published = null; throw failed; }
    }
    private Map<K, V> projectCurrent(Object epoch, Map<K, D> declarations, C context, ImmutableReferenceIndex.Inputs<C> inputs,
                                    List<Object> basis) {
        var ready = published;
        var roots = inputs.roots;
        if (!ImmutableInvariantCheck.fullAudit() && matches(ready, epoch, declarations, roots)) return ready.values();
        var image = current.get();
        if (image == null || image.epoch != epoch || ImmutableInvariantCheck.fullAudit()) {
            image = new Image<>(epoch); current.set(image);
        }
        try {
            references.validate(image.generation, declarations, context, inputs);
            var next = image.values;
            var removed = new HashSet<K>();
            ImmutableMapChanges.forEachRemovedKey(image.declarations, declarations, removed::add);
            var replacements = new HashMap<K, V>();
            for (var entry : image.changes.entrySet())
                if (!Objects.equals(next.get(entry.getKey()), entry.getValue())) replacements.put(entry.getKey(), entry.getValue());
            if (removed.isEmpty() && replacements.keySet().stream().allMatch(next::containsKey))
                next = ImmutableLookupMap.replacing(next, replacements);
            else {
                var changed = new HashMap<>(next); removed.forEach(changed::remove); changed.putAll(replacements);
                next = ImmutableLookupMap.copyOf(changed);
            }
            image.values = next; image.declarations = declarations; image.changes.clear();
            // Never run owner code, scans or equality of large values under this lock.
            synchronized (this) {
                ready = published;
                if (!ImmutableInvariantCheck.fullAudit() && matches(ready, epoch, declarations, roots)) return ready.values();
                published = new Published<>(epoch, declarations, roots, basis == null ? basis(epoch, declarations, roots) : basis, next);
            }
            return next;
        } catch (RuntimeException | Error failed) {
            current.remove(); published = null; throw failed;
        }
    }
    private boolean matches(Published<K, D, V> value, Object epoch, Map<K, D> declarations, List<?> roots) {
        return value != null && value.epoch() == epoch && value.declarations() == declarations
                && ImmutablePlanningScope.same(value.roots(), roots);
    }
    private List<Object> basis(Object epoch, Map<K, D> declarations, List<?> roots) {
        var result = new ArrayList<Object>(roots.size() + 2);
        result.add(epoch); result.add(declarations); result.addAll(roots);
        return List.copyOf(result);
    }
}

package io.farfrontier.palemirror.frontier.v3.model;

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;

/** Computing-thread views of an explicit bounded producer registry. Live rows
 * never evict one another; optional immutable snapshot history is separately
 * bounded and restores the complete input basis. New epochs release old images. */
final class ImmutableRegistryView<K, I, V> {
    private record Working<K, I, V>(Object generation, Map<K, I> source, Map<K, I> basis, Map<K, V> snapshot) { }
    private final int maximumEntries, maximumSnapshots;
    private final BiPredicate<V, V> sameOutput;
    private final ThreadLocal<Working<K, I, V>> current = new ThreadLocal<>();
    private final ThreadLocal<ArrayDeque<Working<K, I, V>>> snapshots = ThreadLocal.withInitial(ArrayDeque::new);
    ImmutableRegistryView(int maximumEntries) { this(maximumEntries, 1); }
    ImmutableRegistryView(int maximumEntries, int maximumSnapshots) {
        this(maximumEntries, maximumSnapshots, (before, after) -> before == after);
    }
    /** The owner explicitly supplies equality of its complete immutable projected value. */
    ImmutableRegistryView(int maximumEntries, BiPredicate<V, V> sameOutput) {
        this(maximumEntries, 1, sameOutput);
    }
    private ImmutableRegistryView(int maximumEntries, int maximumSnapshots, BiPredicate<V, V> sameOutput) {
        this.sameOutput = Objects.requireNonNull(sameOutput);
        if (maximumEntries < 1 || maximumSnapshots < 1) throw new IllegalArgumentException("registry view requires declared positive bounds");
        this.maximumEntries = maximumEntries; this.maximumSnapshots = maximumSnapshots;
    }
    Map<K, V> project(Object generation, Map<K, I> inputs, BiFunction<K, I, V> compute) {
        Objects.requireNonNull(generation); Objects.requireNonNull(inputs); Objects.requireNonNull(compute);
        var working = current.get(); var changes = Optional.<Set<K>>empty();
        if (maximumSnapshots > 1) {
            var recent = snapshots.get();
            if (working == null || working.generation() != generation) recent.clear();
            Working<K, I, V> retained = null;
            if (inputs instanceof ImmutableMapImage<?, ?>) for (var image : recent)
                if (image.generation() == generation && image.source() == inputs) { retained = image; break; }
            if (retained != null) {
                // Restore the complete immutable row basis, not just its result. A
                // subsequent direct replacement can now use its actual predecessor.
                current.set(retained); remember(retained); return retained.snapshot();
            }
        }
        if (working != null && working.generation() == generation && inputs instanceof ImmutableMapImage<?, ?> image) {
            if (working.source() == inputs) { remember(working); return working.snapshot(); }
            @SuppressWarnings("unchecked") var typed = (ImmutableMapImage<K, I>) image;
            changes = typed.changedKeysFrom(working.source());
        }
        var result = project(generation, inputs, (before, after) -> before == after, compute, changes, true);
        remember(current.get());
        return result;
    }
    private void remember(Working<K, I, V> image) {
        if (maximumSnapshots == 1 || !(image.source() instanceof ImmutableMapImage<?, ?>)) return;
        var recent = snapshots.get();
        recent.removeIf(prior -> prior.generation() == image.generation() && prior.source() == image.source());
        if (recent.size() >= maximumSnapshots) recent.removeFirst();
        recent.addLast(image);
    }
    Map<K, V> project(Object generation, Map<K, I> inputs, BiPredicate<I, I> unchanged, BiFunction<K, I, V> compute) {
        // An explicit comparison may deliberately require full reevaluation. Never
        // replace that owner's decision with a direct-change or same-source shortcut.
        if (maximumSnapshots > 1) snapshots.remove();
        return project(generation, inputs, unchanged, compute, Optional.empty(), false);
    }
    private Map<K, V> project(Object generation, Map<K, I> inputs, BiPredicate<I, I> unchanged,
                              BiFunction<K, I, V> compute, Optional<Set<K>> changes, boolean identityComparison) {
        Objects.requireNonNull(generation); Objects.requireNonNull(unchanged); Objects.requireNonNull(compute);
        if (inputs.size() > maximumEntries) throw new IllegalArgumentException("derived registry exceeds its declared producer bound");
        var working = current.get(); boolean sameEpoch = working != null && working.generation() == generation;
        var previous = sameEpoch ? working.snapshot() : Map.<K, V>of();
        var priorInputs = sameEpoch ? working.basis() : Map.<K, I>of();
        boolean useSourceBasis = identityComparison && inputs instanceof ImmutableMapImage<?, ?>;
        var update = new Update<K, I, V>(priorInputs, previous, !useSourceBasis, sameOutput);
        if (sameEpoch && changes.isPresent()) {
            for (var key : changes.orElseThrow()) {
                if (inputs.containsKey(key)) update.accept(key, inputs.get(key), unchanged, compute);
                else update.remove(key);
            }
        } else {
            for (var key : previous.keySet()) if (!inputs.containsKey(key)) update.remove(key);
            inputs.forEach((key, input) -> update.accept(key, input, unchanged, compute));
        }
        var result = update.values();
        // The immutable source already owns row inputs. Retaining a duplicate
        // entry table would copy the whole registry even when every projected
        // value stayed identical (for example, an actor changed only position).
        Map<K, I> basis = useSourceBasis ? inputs : update.basis();
        // All computations finish before any trusted basis is replaced.
        current.set(new Working<>(generation, inputs, basis, result));
        return result;
    }
    private static final class Update<K, I, V> {
        private final Map<K, I> inputs;
        private final Map<K, V> previous;
        private final boolean captureInputs;
        private final BiPredicate<V, V> sameOutput;
        private final Map<K, V> replacements = new HashMap<>();
        private final Map<K, I> inputReplacements = new HashMap<>();
        private final Set<K> removals = new HashSet<>();
        private Update(Map<K, I> inputs, Map<K, V> previous, boolean captureInputs, BiPredicate<V, V> sameOutput) {
            this.inputs = inputs; this.previous = previous; this.captureInputs = captureInputs; this.sameOutput = sameOutput;
        }
        private void accept(K key, I input, BiPredicate<I, I> unchanged, BiFunction<K, I, V> compute) {
            Objects.requireNonNull(key); Objects.requireNonNull(input);
            var prior = previous.get(key);
            if (prior != null && unchanged.test(inputs.get(key), input)) return;
            var value = Objects.requireNonNull(compute.apply(key, input));
            if (captureInputs && inputs.get(key) != input) inputReplacements.put(key, input);
            if (prior == null || !sameOutput.test(prior, value)) replacements.put(key, value);
        }
        private void remove(K key) {
            if (previous.containsKey(key)) removals.add(key);
        }
        private <T> Map<K, T> finish(Map<K, T> source, Map<K, T> changed) {
            if (removals.isEmpty() && changed.keySet().stream().allMatch(source::containsKey))
                return ImmutableLookupMap.replacing(source, changed);
            // Membership changes retain the previous HashMap iteration semantics.
            // Bulk ingress is one copy, never a chain of thousands of insertions.
            var next = new HashMap<>(source);
            removals.forEach(next::remove); next.putAll(changed);
            return ImmutableLookupMap.copyOf(next);
        }
        private Map<K, V> values() { return finish(previous, replacements); }
        private Map<K, I> basis() { return finish(inputs, inputReplacements); }
    }
}

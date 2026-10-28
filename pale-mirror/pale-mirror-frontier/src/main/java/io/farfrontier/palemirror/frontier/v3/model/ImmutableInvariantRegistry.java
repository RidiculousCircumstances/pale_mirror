package io.farfrontier.palemirror.frontier.v3.model;

import java.util.*;

/** Bounded owner-local successful checks, with explicit immutable dependencies.
 * One computing-thread image; withdrawn identities and old epochs are released.
 * Initial/recovery audits always run the original complete check. */
public final class ImmutableInvariantRegistry<K> {
    private record Accepted(Object declaration, ImmutableDependencies dependencies) { }
    private static final class Image<K> {
        final Object epoch;
        Map<K, ?> source;
        final Map<K, Accepted> accepted = new HashMap<>();
        java.lang.ref.WeakReference<Object> snapshot = new java.lang.ref.WeakReference<>(null);
        final Map<K, Object> visited = new HashMap<>();
        Image(Object epoch, Map<K, ?> source) { this.epoch = epoch; this.source = source; }
    }
    private final int maximumEntries;
    private final ThreadLocal<Image<K>> current = new ThreadLocal<>();
    public ImmutableInvariantRegistry(int maximumEntries) {
        if (maximumEntries < 1) throw new IllegalArgumentException("invariant registry needs a declared positive bound");
        this.maximumEntries = maximumEntries;
    }
    public void validate(Object epoch, Map<K, ?> registry, K key, Object declaration, Runnable check, Object... dependencies) {
        Objects.requireNonNull(epoch); Objects.requireNonNull(registry); Objects.requireNonNull(key);
        Objects.requireNonNull(declaration); Objects.requireNonNull(check); Objects.requireNonNull(dependencies);
        var image = image(epoch, registry);
        validate(image, registry, key, declaration, check, dependencies);
    }
    /** Repeated visits to one complete immutable input snapshot need not reconstruct
     * dependency arrays. A changed snapshot still uses the SAME complete read-set proof.
     * The producer supplies the whole immutable aggregate, not its selected owner row.
     * Weak scope retention cannot keep old world histories alive. */
    public void validateSnapshot(Object epoch, Map<K, ?> registry, K key, Object declaration,
            Object immutableSnapshot, Runnable check, java.util.function.Supplier<Object[]> dependencies) {
        Objects.requireNonNull(epoch); Objects.requireNonNull(registry); Objects.requireNonNull(key);
        Objects.requireNonNull(declaration); Objects.requireNonNull(immutableSnapshot);
        Objects.requireNonNull(check); Objects.requireNonNull(dependencies);
        var image = image(epoch, registry);
        if (registry.get(key) != declaration) { check.run(); return; }
        if (image.snapshot.get() != immutableSnapshot) {
            image.snapshot = new java.lang.ref.WeakReference<>(immutableSnapshot); image.visited.clear();
        }
        if (!ImmutableInvariantCheck.fullAudit() && image.visited.get(key) == declaration) return;
        image.visited.remove(key);
        validate(image, registry, key, declaration, check, Objects.requireNonNull(dependencies.get()));
        image.visited.put(key, declaration);
    }
    private Image<K> image(Object epoch, Map<K, ?> registry) {
        if (registry.size() > maximumEntries) throw new IllegalArgumentException("invariant registry exceeds its producer bound");
        var image = current.get();
        if (image == null || image.epoch != epoch) {
            image = new Image<>(epoch, registry); current.set(image);
        } else if (image.source != registry) {
            image.accepted.keySet().removeIf(id -> !registry.containsKey(id));
            image.visited.entrySet().removeIf(entry -> registry.get(entry.getKey()) != entry.getValue());
            image.source = registry;
        }
        return image;
    }
    private void validate(Image<K> image, Map<K, ?> registry, K key, Object declaration, Runnable check, Object[] dependencies) {
        // Unpublished candidates retain ordinary full validation, never evidence
        // attached to another current declaration with the same identifier.
        if (registry.get(key) != declaration) { check.run(); return; }
        var previous = image.accepted.get(key);
        if (!ImmutableInvariantCheck.fullAudit() && previous != null && previous.declaration() == declaration && previous.dependencies().matches(dependencies)) return;
        check.run(); // A failed candidate never installs trusted dependency facts.
        image.accepted.put(key, new Accepted(declaration, ImmutableDependencies.of(dependencies)));
    }
}

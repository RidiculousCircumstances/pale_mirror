package io.farfrontier.palemirror.frontier.v3.model;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/** Bounded deterministic once-only gate; callers retain the actual effect in their existing owner. */
public final class OperationFrontEffectCoordinator {
    private static final int MAX_EFFECTS = 256;
    private final Set<OperationFrontEffectKey> applied;
    public OperationFrontEffectCoordinator(Set<OperationFrontEffectKey> applied) {
        this.applied = Set.copyOf(Objects.requireNonNull(applied, "front effects"));
        if (this.applied.size() > MAX_EFFECTS) throw new IllegalArgumentException("front effect retention exceeded");
    }
    public static OperationFrontEffectCoordinator empty() { return new OperationFrontEffectCoordinator(Set.of()); }
    /** Snapshot-visible receipts ordered by their exact cause and front identities. */
    public Set<OperationFrontEffectKey> applied() { return applied; }
    public boolean accepts(OperationFrontEffectKey key) { return !applied.contains(Objects.requireNonNull(key, "front effect key")); }
    public OperationFrontEffectCoordinator record(OperationFrontEffectKey key) {
        if (!accepts(key)) throw new IllegalArgumentException("cross-front effect is already applied");
        LinkedHashSet<OperationFrontEffectKey> next = new LinkedHashSet<>(applied); next.add(key);
        if (next.size() > MAX_EFFECTS) throw new IllegalStateException("active front effect retention exhausted");
        return new OperationFrontEffectCoordinator(next);
    }
    @Override public boolean equals(Object other) {
        return other instanceof OperationFrontEffectCoordinator value && applied.equals(value.applied);
    }
    @Override public int hashCode() { return applied.hashCode(); }
}

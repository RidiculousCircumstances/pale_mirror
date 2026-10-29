package io.farfrontier.palemirror.frontier.v3.model;

import java.util.*;
import java.util.function.Supplier;

/** Bounded disposable memoization for branching immutable plans, never canonical authority. */
public final class ImmutablePlanningScope {
    private static final int MAX_VIEWS = 64, MAX_PUBLICATIONS = 16, MAX_BASES_PER_VIEW = 64;
    private static final ThreadLocal<ImmutablePlanningScope> ACTIVE = new ThreadLocal<>();
    private static final class View {
        final Map<Basis, Object> bases = new HashMap<>();
        List<Object> lastInputs = List.of();
        Object lastValue;
        void remember(List<Object> inputs, Object value) {
            lastInputs = inputs; lastValue = value;
        }
    }
    private final Map<Object, View> views = new IdentityHashMap<>();
    private Map<Object, View> publications;
    /** Hash selects a bucket only; every input still matches by exact identity. */
    private static final class Basis {
        private final List<Object> inputs;
        private final int hash;
        Basis(List<Object> inputs) {
            this.inputs = inputs;
            int value = 1;
            for (var input : inputs) value = 31 * value + System.identityHashCode(input);
            hash = value;
        }
        @Override public int hashCode() { return hash; }
        @Override public boolean equals(Object other) {
            return other instanceof Basis basis && same(inputs, basis.inputs);
        }
    }
    private ImmutablePlanningScope() { }

    static boolean active() { return ACTIVE.get() != null; }

    public static <T> T within(Supplier<T> action) {
        Objects.requireNonNull(action);
        if (ACTIVE.get() != null) return action.get();
        ACTIVE.set(new ImmutablePlanningScope());
        try { return action.get(); }
        finally { ACTIVE.remove(); }
    }
    static <T> T get(Object view, List<Object> inputs, Supplier<T> compute) {
        var scope = ACTIVE.get();
        if (scope == null) return compute.get();
        return get(scope.views, MAX_VIEWS, view, inputs, compute);
    }
    /** Complete producer facts have their own bounded allowance. Registering
     * them cannot crowd the pre-existing mutable planning views out of history. */
    static <T> T publication(Object view, List<Object> inputs, Supplier<T> compute) {
        var scope = ACTIVE.get();
        if (scope == null) return compute.get();
        if (scope.publications == null) scope.publications = new IdentityHashMap<>();
        return get(scope.publications, MAX_PUBLICATIONS, view, inputs, compute);
    }
    private static <T> T get(Map<Object, View> views, int maximumViews, Object view, List<Object> inputs, Supplier<T> compute) {
        var entries = views.get(view);
        // Preserve the complete bounded branch history, but the common repeated
        // read needs only pointer comparisons, not another temporary hash key.
        if (entries != null && entries.lastValue != null && same(entries.lastInputs, inputs)) {
            @SuppressWarnings("unchecked") T result = (T) entries.lastValue;
            return result;
        }
        var basis = new Basis(inputs);
        if (entries != null) {
            // Each view identity is one typed producer; no heterogeneous value registry.
            @SuppressWarnings("unchecked") T result = (T) entries.bases.get(basis);
            if (result != null) {
                entries.remember(List.copyOf(inputs), result);
                return result;
            }
        }
        T value = Objects.requireNonNull(compute.get());
        if (entries == null && views.size() < maximumViews) {
            entries = new View(); views.put(view, entries);
        }
        if (entries != null && entries.bases.size() < MAX_BASES_PER_VIEW) {
            var retained = new Basis(List.copyOf(inputs));
            entries.bases.put(retained, value); entries.remember(retained.inputs, value);
        }
        return value;
    }
    static boolean same(List<?> left, List<?> right) {
        if (left.size() != right.size()) return false;
        for (int i = 0; i < left.size(); i++) if (left.get(i) != right.get(i)) return false;
        return true;
    }
}

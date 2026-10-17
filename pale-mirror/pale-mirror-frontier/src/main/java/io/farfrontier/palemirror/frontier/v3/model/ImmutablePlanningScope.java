package io.farfrontier.palemirror.frontier.v3.model;

import java.util.*;
import java.util.function.Supplier;

/** Bounded disposable memoization for branching immutable plans, never canonical authority. */
public final class ImmutablePlanningScope {
    private static final int MAX_VIEWS = 64, MAX_BASES_PER_VIEW = 64;
    private static final ThreadLocal<ImmutablePlanningScope> ACTIVE = new ThreadLocal<>();
    private final Map<Object, List<Entry>> views = new IdentityHashMap<>();
    private record Entry(List<Object> inputs, Object value) { }
    private ImmutablePlanningScope() { }

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
        var entries = scope.views.get(view);
        if (entries != null) for (var entry : entries) if (same(entry.inputs(), inputs)) {
            // Each view identity is one typed producer; no heterogeneous value registry.
            @SuppressWarnings("unchecked") T result = (T) entry.value();
            return result;
        }
        T value = Objects.requireNonNull(compute.get());
        if (entries == null && scope.views.size() < MAX_VIEWS) {
            entries = new ArrayList<>(); scope.views.put(view, entries);
        }
        if (entries != null && entries.size() < MAX_BASES_PER_VIEW)
            entries.add(new Entry(List.copyOf(inputs), value));
        return value;
    }
    static boolean same(List<Object> left, List<Object> right) {
        if (left.size() != right.size()) return false;
        for (int i = 0; i < left.size(); i++) if (left.get(i) != right.get(i)) return false;
        return true;
    }
}

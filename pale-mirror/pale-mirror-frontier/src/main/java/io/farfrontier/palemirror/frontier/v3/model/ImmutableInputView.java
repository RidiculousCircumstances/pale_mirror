package io.farfrontier.palemirror.frontier.v3.model;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/** One bounded disposable derived view, keyed by exact immutable input identities.
 * Never persisted, never authoritative; changed inputs are recomputed before use. */
public final class ImmutableInputView<T> {
    private List<Object> inputs = List.of();
    private T value;
    public synchronized T get(List<Object> current, Supplier<T> compute) {
        return ImmutablePlanningScope.get(this, current, () -> {
            if (value == null || !ImmutablePlanningScope.same(inputs, current)) {
                T next = Objects.requireNonNull(compute.get());
                inputs = List.copyOf(current); value = next;
            }
            return value;
        });
    }
}

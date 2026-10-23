package io.farfrontier.palemirror.frontier.v3.model;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/** One bounded disposable derived view, keyed by exact immutable input identities.
 * Never persisted, never authoritative; changed inputs are recomputed before use. */
public final class ImmutableInputView<T> {
    private static final class Entry<T> {
        List<Object> inputs = List.of(); T value;
    }
    private final ThreadLocal<Entry<T>> local = ThreadLocal.withInitial(Entry::new);
    public T get(List<Object> current, Supplier<T> compute) {
        return ImmutablePlanningScope.get(this, current, () -> {
            var entry = local.get();
            if (entry.value == null || !ImmutablePlanningScope.same(entry.inputs, current)) {
                T next = Objects.requireNonNull(compute.get());
                entry.inputs = List.copyOf(current); entry.value = next;
            }
            return entry.value;
        });
    }
}

package io.farfrontier.palemirror.frontier.v3.model;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/** One bounded disposable derived view, keyed by exact immutable input identities.
 * Never persisted, never authoritative; changed inputs are recomputed before use. */
public final class ImmutableInputView<T> {
    /** Plain thread-confined row; a keyed directory owns its isolation and lifetime. */
    static final class Entry<T> {
        private List<Object> inputs = List.of();
        private T value;
        T get(List<Object> current, Supplier<T> compute, boolean shareEqualValue) {
            if (value == null || !ImmutablePlanningScope.same(inputs, current)) {
                T next = Objects.requireNonNull(compute.get());
                T accepted = shareEqualValue && Objects.equals(value, next) ? value : next;
                var acceptedInputs = List.copyOf(current);
                inputs = acceptedInputs; value = accepted;
            }
            return value;
        }
    }
    private final ThreadLocal<Entry<T>> local = ThreadLocal.withInitial(Entry::new);
    public T get(List<Object> current, Supplier<T> compute) {
        return get(current, compute, false);
    }
    /** Only complete immutable values may cut off downstream invalidation by equality. */
    public T getPublished(List<Object> current, Supplier<T> compute) {
        return get(current, compute, true);
    }
    private T get(List<Object> current, Supplier<T> compute, boolean shareEqualValue) {
        return ImmutablePlanningScope.get(this, current, () -> local.get().get(current, compute, shareEqualValue));
    }
}

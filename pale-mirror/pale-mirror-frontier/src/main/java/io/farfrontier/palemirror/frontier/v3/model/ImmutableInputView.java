package io.farfrontier.palemirror.frontier.v3.model;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/** One bounded disposable derived view, keyed by exact immutable input identities.
 * Never persisted, never authoritative; changed inputs are recomputed before use. */
final class ImmutableInputView<T> {
    private List<Object> inputs = List.of();
    private T value;
    synchronized T get(List<Object> current, Supplier<T> compute) {
        boolean same = value != null && inputs.size() == current.size();
        for (int i = 0; same && i < current.size(); i++) same = inputs.get(i) == current.get(i);
        if (!same) {
            T next = Objects.requireNonNull(compute.get());
            inputs = List.copyOf(current); value = next;
        }
        return value;
    }
}

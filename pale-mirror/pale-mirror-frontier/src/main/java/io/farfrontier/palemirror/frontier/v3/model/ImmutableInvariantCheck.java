package io.farfrontier.palemirror.frontier.v3.model;

import java.util.Objects;
import java.util.function.Supplier;

/** One owner-local successful validation image, never a bypass for changed
 * dependencies. Full bootstrap/recovery audits ignore all cached evidence.
 * The owner supplies both its check and its complete immutable read set. */
final class ImmutableInvariantCheck<T> {
    private record Accepted<T>(ImmutableDependencies dependencies, T result) { }
    private final ThreadLocal<Accepted<T>> accepted = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> FULL = new ThreadLocal<>();

    T value(Supplier<T> check, Object... inputs) {
        Objects.requireNonNull(check, "owned invariant check");
        var previous = accepted.get();
        if (!fullAudit() && previous != null && previous.dependencies().matches(inputs)) return previous.result();
        T result = check.get(); // a failing check cannot install trusted inputs
        accepted.set(new Accepted<>(ImmutableDependencies.of(inputs), result));
        return result;
    }
    void validate(Runnable check, Object... inputs) {
        value(() -> { check.run(); return null; }, inputs);
    }
    static boolean fullAudit() { return Boolean.TRUE.equals(FULL.get()); }
    static <R> R full(Supplier<R> operation) {
        var old = FULL.get(); FULL.set(true);
        try { return operation.get(); }
        finally { if (old == null) FULL.remove(); else FULL.set(old); }
    }
}

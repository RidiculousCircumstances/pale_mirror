package io.farfrontier.palemirror.frontier.v3.model;

import java.util.Objects;
import java.util.function.Supplier;

/** One owner-local successful validation image, never a bypass for changed
 * dependencies. Full bootstrap/recovery audits ignore all cached evidence.
 * The owner supplies both its check and its complete immutable read set. */
final class ImmutableInvariantCheck<T> {
    private record Accepted<T>(ImmutableDependencies dependencies, T result) { }
    private final ThreadLocal<Accepted<T>> accepted = new ThreadLocal<>();
    private final String traceKind;
    ImmutableInvariantCheck() { this.traceKind = null; }
    ImmutableInvariantCheck(String traceKind) { this.traceKind = Objects.requireNonNull(traceKind); }

    T value(Supplier<T> check, Object... inputs) {
        Objects.requireNonNull(check, "owned invariant check");
        var previous = accepted.get();
        if (!fullAudit() && previous != null && previous.dependencies().matches(inputs)) return previous.result();
        T result;
        if (traceKind == null) result = check.get();
        else try (var ignored = ImmutableValidationScope.detail(traceKind)) { result = check.get(); }
        // A failing check cannot install trusted inputs.
        accepted.set(new Accepted<>(ImmutableDependencies.of(inputs), result));
        return result;
    }
    void validate(Runnable check, Object... inputs) {
        value(() -> { check.run(); return null; }, inputs);
    }
    static boolean fullAudit() { return ImmutableValidationScope.fullAudit(); }
    static <R> R full(Supplier<R> operation) {
        return ImmutableValidationScope.full(operation);
    }
}

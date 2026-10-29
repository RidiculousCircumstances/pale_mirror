package io.farfrontier.palemirror.frontier.v3.model;

import java.util.Objects;
import java.util.function.Supplier;
import io.farfrontier.palemirror.frontier.v3.kernel.FrontierExecutionMetrics;

/** A full ingress/recovery audit cannot reuse derived successful-check evidence.
 * Thread-scoped validation mode only; it grants no mutation or execution authority. */
public final class ImmutableValidationScope {
    private static final ThreadLocal<Boolean> FULL = new ThreadLocal<>();
    private record Trace(FrontierExecutionMetrics metrics, String owner) { }
    private static final FrontierExecutionMetrics.Span UNOBSERVED = () -> { };
    private static final ThreadLocal<Trace> TRACE = new ThreadLocal<>();
    private ImmutableValidationScope() { }

    public static boolean fullAudit() { return Boolean.TRUE.equals(FULL.get()); }

    static <T> T observed(FrontierExecutionMetrics metrics, String owner, Supplier<T> operation) {
        if (!FrontierExecutionMetrics.safelyDetailsEnabled(metrics)) return operation.get();
        var previous = TRACE.get(); TRACE.set(new Trace(metrics, owner));
        try { return operation.get(); }
        finally { if (previous == null) TRACE.remove(); else TRACE.set(previous); }
    }
    static FrontierExecutionMetrics.Span detail(String kind) {
        var trace = TRACE.get();
        return trace == null ? UNOBSERVED
                : FrontierExecutionMetrics.safelyDetail(trace.metrics(), FrontierExecutionMetrics.Stage.VALIDATION, kind, trace.owner());
    }

    public static <T> T full(Supplier<T> operation) {
        Objects.requireNonNull(operation);
        var previous = FULL.get(); FULL.set(true);
        try { return operation.get(); }
        finally { if (previous == null) FULL.remove(); else FULL.set(previous); }
    }
}

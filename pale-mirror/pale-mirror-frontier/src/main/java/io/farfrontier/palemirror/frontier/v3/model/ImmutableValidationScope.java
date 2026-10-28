package io.farfrontier.palemirror.frontier.v3.model;

import java.util.Objects;
import java.util.function.Supplier;

/** A full ingress/recovery audit cannot reuse derived successful-check evidence.
 * Thread-scoped validation mode only; it grants no mutation or execution authority. */
public final class ImmutableValidationScope {
    private static final ThreadLocal<Boolean> FULL = new ThreadLocal<>();
    private ImmutableValidationScope() { }

    public static boolean fullAudit() { return Boolean.TRUE.equals(FULL.get()); }

    public static <T> T full(Supplier<T> operation) {
        Objects.requireNonNull(operation);
        var previous = FULL.get(); FULL.set(true);
        try { return operation.get(); }
        finally { if (previous == null) FULL.remove(); else FULL.set(previous); }
    }
}

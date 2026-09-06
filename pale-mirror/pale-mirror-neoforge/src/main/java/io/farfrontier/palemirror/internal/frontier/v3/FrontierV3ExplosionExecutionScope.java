package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId;

import java.util.Optional;

/** Identifies only the synchronous vanilla detonation of one exact owned bomber body. */
final class FrontierV3ExplosionExecutionScope {
    private static final ThreadLocal<PhysicalIntentId> CURRENT = new ThreadLocal<>();

    private FrontierV3ExplosionExecutionScope() { }

    static void run(PhysicalIntentId intentId, Runnable effect) {
        if (CURRENT.get() != null) throw new IllegalStateException("nested v3 explosion execution is not allowed");
        CURRENT.set(intentId);
        try { effect.run(); } finally { CURRENT.remove(); }
    }

    static Optional<PhysicalIntentId> currentIntent() { return Optional.ofNullable(CURRENT.get()); }
}

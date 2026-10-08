package io.farfrontier.palemirror.internal.frontier.v3;

import java.util.Objects;
import java.util.function.LongSupplier;

/** Host-only soft admission limit. Never interrupts a started custody transition. */
final class FrontierV3PhysicalTransitionBudget {
    private final LongSupplier clock;
    private final long startedAt;
    private final long maximumNanos;
    private boolean admitted;

    FrontierV3PhysicalTransitionBudget(long maximumNanos, LongSupplier clock) {
        if (maximumNanos <= 0) throw new IllegalArgumentException("positive physical transition budget required");
        this.maximumNanos = maximumNanos;
        this.clock = Objects.requireNonNull(clock, "clock");
        startedAt = clock.getAsLong();
    }

    boolean tryStart() {
        // At least one complete transition progresses even when earlier host work
        // exhausted this soft turn. Further work stays retained for the next tick.
        if (admitted && clock.getAsLong() - startedAt >= maximumNanos) return false;
        admitted = true;
        return true;
    }
}

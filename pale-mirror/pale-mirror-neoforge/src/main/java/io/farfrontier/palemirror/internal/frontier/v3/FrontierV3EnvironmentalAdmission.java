package io.farfrontier.palemirror.internal.frontier.v3;

import java.util.Objects;
import java.util.function.LongSupplier;

/** Shared native-tick allowance for optional random samples, including their canonical
 * ingress. Exhaustion resamples next tick; it never queues or replays a physical effect. */
final class FrontierV3EnvironmentalAdmission {
    private final LongSupplier clock;
    private long spent, allowance = Long.MAX_VALUE;
    private boolean active;
    FrontierV3EnvironmentalAdmission(LongSupplier clock) { this.clock = Objects.requireNonNull(clock); }
    void begin(long nanos) {
        if (active || nanos < 1) throw new IllegalArgumentException("invalid environmental turn allowance");
        spent = 0; allowance = nanos;
    }
    boolean run(Runnable sample) {
        if (spent >= allowance || active) return false;
        long start = clock.getAsLong(); active = true;
        try { sample.run(); }
        finally { spent = Math.addExact(spent, Math.max(0L, clock.getAsLong() - start)); active = false; }
        return true;
    }
}

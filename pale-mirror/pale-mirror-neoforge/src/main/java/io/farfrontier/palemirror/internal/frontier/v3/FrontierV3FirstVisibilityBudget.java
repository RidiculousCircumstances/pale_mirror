package io.farfrontier.palemirror.internal.frontier.v3;

import java.util.function.LongSupplier;

/** Host admission only: cheap reads do not spend the physical mutation allowance. */
final class FrontierV3FirstVisibilityBudget {
    private final int readLimit;
    private final int writeLimit;
    private final long softNanos;
    private final LongSupplier clock;
    private final long started;
    private int reads;
    private int writes;

    FrontierV3FirstVisibilityBudget(int readLimit, int writeLimit, long softNanos, LongSupplier clock) {
        if (readLimit < 1 || writeLimit < 1 || softNanos < 1)
            throw new IllegalArgumentException("invalid first visibility admission budget");
        this.readLimit = readLimit;
        this.writeLimit = writeLimit;
        this.softNanos = softNanos;
        this.clock = java.util.Objects.requireNonNull(clock);
        this.started = clock.getAsLong();
    }

    boolean takeRead() {
        // Admit one complete attempt even under pressure; never interrupt an admitted write.
        if (!canRead()) return false;
        reads++;
        return true;
    }

    boolean canRead() {
        return reads < readLimit && (reads == 0 || clock.getAsLong() - started < softNanos);
    }

    boolean takeWrite() {
        if (writes >= writeLimit) return false;
        writes++;
        return true;
    }
}

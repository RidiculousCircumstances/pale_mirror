package io.farfrontier.palemirror.frontier.v3.kernel;

/** Bounded in-memory retention and future-work capacity; exhaustion is a visible rejection. */
public record EngineLimits(int maxReceipts, long receiptWindowTicks, int maxTransactions, int maxPendingSchedules) {
    public EngineLimits {
        if (maxReceipts <= 0 || receiptWindowTicks < 0L || maxTransactions <= 0 || maxPendingSchedules <= 0) {
            throw new IllegalArgumentException("engine limits must be positive and receipt window non-negative");
        }
    }

    /** Keeps existing small fixtures bounded instead of leaving their future work unbounded. */
    public EngineLimits(int maxReceipts, long receiptWindowTicks, int maxTransactions) {
        this(maxReceipts, receiptWindowTicks, maxTransactions, maxTransactions);
    }
}

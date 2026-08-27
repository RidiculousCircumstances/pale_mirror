package io.farfrontier.palemirror.frontier.v3.kernel;

/** Bounded in-memory retention for the Wave 1 test store; exhaustion is a visible rejection. */
public record EngineLimits(int maxReceipts, long receiptWindowTicks, int maxTransactions) {
    public EngineLimits {
        if (maxReceipts <= 0 || receiptWindowTicks < 0L || maxTransactions <= 0) {
            throw new IllegalArgumentException("engine limits must be positive and receipt window non-negative");
        }
    }
}

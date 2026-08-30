package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Comparator;

/** Immutable bounded terminal-logistics receipt owner. */
final class LogisticsHistory {
    static final int MAX_RECEIPTS = 256;
    private final Map<SubjectId, TerminalLogisticsReceipt> receipts;
    private final long deliveredCount, failedCount, interruptedCount;

    LogisticsHistory(Map<SubjectId, TerminalLogisticsReceipt> receipts, long deliveredCount, long failedCount, long interruptedCount) {
        this.receipts = Map.copyOf(new LinkedHashMap<>(Objects.requireNonNull(receipts, "receipts")));
        if (this.receipts.size() > MAX_RECEIPTS) throw new IllegalArgumentException("terminal logistics receipt retention limit exceeded");
        if (deliveredCount < 0L || failedCount < 0L || interruptedCount < 0L) throw new IllegalArgumentException("terminal logistics aggregate count must be non-negative");
        this.deliveredCount = deliveredCount; this.failedCount = failedCount; this.interruptedCount = interruptedCount;
        this.receipts.forEach((id, receipt) -> { if (!id.equals(receipt.operationId())) throw new IllegalArgumentException("receipt key must be its operation identity"); });
    }

    static LogisticsHistory empty() { return new LogisticsHistory(Map.of(), 0L, 0L, 0L); }
    Map<SubjectId, TerminalLogisticsReceipt> receipts() { return receipts; }
    long deliveredCount() { return deliveredCount; }
    long failedCount() { return failedCount; }
    long interruptedCount() { return interruptedCount; }

    LogisticsHistory record(TerminalLogisticsReceipt receipt) {
        Objects.requireNonNull(receipt, "terminal receipt");
        if (receipts.containsKey(receipt.operationId())) throw new IllegalArgumentException("terminal operation already has a receipt");
        Map<SubjectId, TerminalLogisticsReceipt> next = new LinkedHashMap<>(receipts);
        if (next.size() == MAX_RECEIPTS) {
            SubjectId oldest = next.values().stream().min(Comparator.comparingLong(TerminalLogisticsReceipt::terminalAtTick)
                    .thenComparing(TerminalLogisticsReceipt::operationId)).orElseThrow().operationId();
            next.remove(oldest);
        }
        next.put(receipt.operationId(), receipt);
        return switch (receipt.outcome()) {
            case DELIVERED -> new LogisticsHistory(next, Math.addExact(deliveredCount, 1L), failedCount, interruptedCount);
            case FAILED -> new LogisticsHistory(next, deliveredCount, Math.addExact(failedCount, 1L), interruptedCount);
            case INTERRUPTED -> new LogisticsHistory(next, deliveredCount, failedCount, Math.addExact(interruptedCount, 1L));
        };
    }

    @Override public boolean equals(Object other) {
        return other instanceof LogisticsHistory history && receipts.equals(history.receipts) && deliveredCount == history.deliveredCount
                && failedCount == history.failedCount && interruptedCount == history.interruptedCount;
    }
    @Override public int hashCode() { return Objects.hash(receipts, deliveredCount, failedCount, interruptedCount); }
}

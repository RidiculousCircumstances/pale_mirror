package io.farfrontier.palemirror.frontier.v3.kernel;

import io.farfrontier.palemirror.frontier.v3.api.SimInstant;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Non-canonical, bounded execution measurement boundary.
 *
 * <p>The kernel reports named work stages but never reads a clock, retains a sample, or changes
 * its order from this interface. A server adapter may time these spans; tests may use a
 * deterministic recorder. Metrics are deliberately absent from snapshots and WAL.</p>
 */
public interface FrontierExecutionMetrics {
    enum Stage { COMMAND_PLAN, SCHEDULE_ALLOCATION, SCHEDULE_PLAN, REDUCTION, VALIDATION, TRANSACTION, PHYSICAL }

    interface Span extends AutoCloseable {
        @Override void close();
    }

    record StageSample(Stage stage, String kind, String owner, long samples, long totalNanos,
                       long maxNanos, long p50UpperNanos, long p95UpperNanos, long p99UpperNanos) {
        public StageSample {
            stage = Objects.requireNonNull(stage, "stage");
            kind = requireLabel(kind, "kind"); owner = requireLabel(owner, "owner");
            if (samples < 0L || totalNanos < 0L || maxNanos < 0L || p50UpperNanos < 0L || p95UpperNanos < 0L || p99UpperNanos < 0L) {
                throw new IllegalArgumentException("execution measurements cannot be negative");
            }
        }
    }

    record QueueSample(String kind, String owner, long samples, int currentDepth, int maxDepth,
                       long currentLagTicks, long maxLagTicks) {
        public QueueSample {
            kind = requireLabel(kind, "kind"); owner = requireLabel(owner, "owner");
            if (samples < 0L || currentDepth < 0 || maxDepth < 0 || currentLagTicks < 0L || maxLagTicks < 0L) {
                throw new IllegalArgumentException("queue measurements cannot be negative");
            }
        }
    }

    record Snapshot(List<StageSample> stages, List<QueueSample> queues, long droppedAttributions) {
        public Snapshot {
            stages = List.copyOf(stages); queues = List.copyOf(queues);
            if (droppedAttributions < 0L) throw new IllegalArgumentException("dropped attributions cannot be negative");
        }

        public static Snapshot empty() { return new Snapshot(List.of(), List.of(), 0L); }
    }

    Span begin(Stage stage, String kind, String owner);

    /** Reports the current exact queue size and, when present, the first due action denied by budget. */
    void observeQueue(SimInstant instant, int queueDepth, Optional<ScheduledAction> deferred);

    Snapshot snapshot();

    static FrontierExecutionMetrics noOp() { return NoOp.INSTANCE; }

    /**
     * Starts one observational span without granting an adapter authority over canonical work.
     * A diagnostic collector can be disabled or fail, but it must never defer, reject or
     * quarantine a simulation transition merely because its own bookkeeping failed.
     */
    static Span safelyBegin(FrontierExecutionMetrics metrics, Stage stage, String kind, String owner) {
        Objects.requireNonNull(metrics, "execution metrics");
        try {
            Span delegate = metrics.begin(stage, kind, owner);
            if (delegate == null) return NoOp.SPAN;
            return () -> {
                try {
                    delegate.close();
                } catch (RuntimeException ignored) {
                    // Diagnostic state is non-canonical and cannot change the work outcome.
                }
            };
        } catch (RuntimeException ignored) {
            return NoOp.SPAN;
        }
    }

    /** Reports observational queue state with the same non-interference guarantee as spans. */
    static void safelyObserveQueue(FrontierExecutionMetrics metrics, SimInstant instant, int queueDepth,
                                   Optional<ScheduledAction> deferred) {
        Objects.requireNonNull(metrics, "execution metrics");
        try {
            metrics.observeQueue(instant, queueDepth, deferred);
        } catch (RuntimeException ignored) {
            // Diagnostic state is non-canonical and cannot change the work outcome.
        }
    }

    private static String requireLabel(String value, String role) {
        value = Objects.requireNonNull(value, role).trim();
        if (value.isEmpty() || value.length() > 128) throw new IllegalArgumentException(role + " must be a bounded label");
        return value;
    }

    enum NoOp implements FrontierExecutionMetrics {
        INSTANCE;
        static final Span SPAN = () -> { };
        @Override public Span begin(Stage stage, String kind, String owner) { return SPAN; }
        @Override public void observeQueue(SimInstant instant, int queueDepth, Optional<ScheduledAction> deferred) { }
        @Override public Snapshot snapshot() { return Snapshot.empty(); }
    }
}

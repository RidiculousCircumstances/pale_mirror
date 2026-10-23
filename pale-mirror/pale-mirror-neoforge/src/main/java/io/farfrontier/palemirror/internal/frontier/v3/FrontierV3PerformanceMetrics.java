package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.SimInstant;
import io.farfrontier.palemirror.frontier.v3.kernel.FrontierExecutionMetrics;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduledAction;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Bounded, server-thread-only timing collector for the v3 execution boundary.
 *
 * <p>It is diagnostic state only: no value reaches the canonical engine, WAL or snapshot. The
 * histogram exposes conservative logarithmic percentile upper bounds without retaining a
 * server-lifetime list of samples.</p>
 */
final class FrontierV3PerformanceMetrics implements FrontierExecutionMetrics {
    private static final int MAX_STAGE_ATTRIBUTIONS = 256;
    private static final int MAX_QUEUE_ATTRIBUTIONS = 256;
    private final Map<Key, Timing> stages = new TreeMap<>();
    private final Map<QueueKey, Queue> queues = new TreeMap<>();
    private long droppedAttributions;
    private long auditReadyWithoutWake;
    private QueuePressure pressure;
    private long hostSamples, longestWorkNanos;
    private Key longestWork;

    @Override
    public Span begin(Stage stage, String kind, String owner) {
        Key key = new Key(stage, kind, owner); long started = System.nanoTime();
        if (stage == Stage.HOST_TICK) { longestWork = null; longestWorkNanos = 0L; }
        return () -> {
            long elapsed = Math.max(0L, System.nanoTime() - started);
            timing(key).record(elapsed);
            if (stage != Stage.HOST_TICK && stage != Stage.HOST_TURN && elapsed > longestWorkNanos) {
                longestWork = key; longestWorkNanos = elapsed;
            }
            if (stage == Stage.HOST_TICK) {
                hostSamples++;
                // Exact outliers, not a sampled recent-64 window: diagnostic only.
                if (elapsed > 80_000_000L) io.farfrontier.palemirror.PaleMirrorMod.LOGGER.info(
                        "PMV3_SLOW_TICK sample={} durationNanos={} longestStage={} longestNanos={}",
                        hostSamples, elapsed, longestWork, longestWorkNanos);
            }
            if (stage == Stage.HOST_TURN && kind.equals("pm-ready-continuation") && elapsed > 80_000_000L)
                io.farfrontier.palemirror.PaleMirrorMod.LOGGER.info(
                        "PMV3_SLOW_READY_TURN durationNanos={} longestStage={} longestNanos={}", elapsed, longestWork, longestWorkNanos);
        };
    }

    @Override
    public void observeQueue(SimInstant instant, int queueDepth, Optional<ScheduledAction> deferred) {
        if (queueDepth < 0) throw new IllegalArgumentException("queue depth cannot be negative");
        ScheduledAction action = deferred.orElse(null);
        QueueKey key = action == null ? new QueueKey("idle", "world") : new QueueKey(action.kind(), action.subject().value());
        long lag = action == null ? 0L : Math.max(0L, instant.ticks() - action.dueAt().ticks());
        queue(key).record(queueDepth, lag, instant.ticks());
        queue(new QueueKey("budget-deferred", "world")).record(action == null ? 0 : 1, lag, instant.ticks());
    }

    @Override
    public void observeQueue(SimInstant instant, int queueDepth, int parkedDepth,
                             Optional<ScheduledAction> deferred) {
        if (parkedDepth < 0 || parkedDepth > queueDepth)
            throw new IllegalArgumentException("parked depth exceeds canonical queue depth");
        observeQueue(instant, queueDepth, deferred);
        queue(new QueueKey("held-waiters", "world")).record(parkedDepth, 0L, instant.ticks());
        queue(new QueueKey("unparked-depth", "world")).record(queueDepth - parkedDepth,
                deferred.map(action -> Math.max(0L, instant.ticks() - action.dueAt().ticks())).orElse(0L), instant.ticks());
    }

    @Override public void observePressure(QueuePressure value) { pressure = value; }

    @Override
    public void observeWakeAudit(long count) {
        if (count < 0L) throw new IllegalArgumentException("wake audit count cannot be negative");
        auditReadyWithoutWake = count;
    }

    @Override
    public Snapshot snapshot() {
        List<StageSample> stageSamples = new ArrayList<>(stages.size());
        stages.forEach((key, value) -> stageSamples.add(value.snapshot(key)));
        List<QueueSample> queueSamples = new ArrayList<>(queues.size());
        queues.forEach((key, value) -> queueSamples.add(value.snapshot(key)));
        return new Snapshot(stageSamples, queueSamples, droppedAttributions, auditReadyWithoutWake, Optional.ofNullable(pressure));
    }

    private Timing timing(Key requested) {
        Timing current = stages.get(requested);
        if (current != null) return current;
        Key selected = stages.size() < MAX_STAGE_ATTRIBUTIONS || requested.stage() == Stage.HOST_TURN || requested.stage() == Stage.HOST_TICK
                ? requested : new Key(requested.stage(), "other", "other");
        if (!selected.equals(requested)) droppedAttributions = droppedAttributions == Long.MAX_VALUE ? Long.MAX_VALUE : droppedAttributions + 1L;
        return stages.computeIfAbsent(selected, ignored -> new Timing());
    }

    private Queue queue(QueueKey requested) {
        Queue current = queues.get(requested);
        if (current != null) return current;
        QueueKey selected = queues.size() < MAX_QUEUE_ATTRIBUTIONS ? requested : new QueueKey("other", "other");
        if (!selected.equals(requested)) droppedAttributions = droppedAttributions == Long.MAX_VALUE ? Long.MAX_VALUE : droppedAttributions + 1L;
        return queues.computeIfAbsent(selected, ignored -> new Queue());
    }

    private record Key(Stage stage, String kind, String owner) implements Comparable<Key> {
        @Override public int compareTo(Key other) {
            int byStage = stage.compareTo(other.stage); if (byStage != 0) return byStage;
            int byKind = kind.compareTo(other.kind); return byKind != 0 ? byKind : owner.compareTo(other.owner);
        }
    }

    private record QueueKey(String kind, String owner) implements Comparable<QueueKey> {
        @Override public int compareTo(QueueKey other) {
            int byKind = kind.compareTo(other.kind); return byKind != 0 ? byKind : owner.compareTo(other.owner);
        }
    }

    private static final class Timing {
        private final long[] bins = new long[64];
        private final long[] recent = new long[64];
        private int recentCursor, recentCount;
        private long samples, totalNanos, maxNanos;

        private void record(long nanos) {
            samples = saturatingAdd(samples, 1L); totalNanos = saturatingAdd(totalNanos, nanos); maxNanos = Math.max(maxNanos, nanos);
            int bin = bin(nanos); bins[bin] = saturatingAdd(bins[bin], 1L);
            recent[recentCursor] = nanos; recentCursor = (recentCursor + 1) % recent.length;
            recentCount = Math.min(recent.length, recentCount + 1);
        }

        private StageSample snapshot(Key key) {
            return new StageSample(key.stage(), key.kind(), key.owner(), samples, totalNanos, maxNanos,
                    percentile(50), percentile(95), percentile(99), recentCount == 0 ? 0 : recent[(recentCursor + recent.length - 1) % recent.length],
                    recentCount, java.util.Arrays.stream(recent).reduce(0L, Timing::saturatingAdd), java.util.Arrays.stream(recent).max().orElse(0));
        }

        private long percentile(int percentile) {
            if (samples == 0L) return 0L;
            long wholeHundreds = samples / 100L, remainder = samples % 100L;
            long required = Math.max(1L, wholeHundreds * percentile + Math.ceilDiv(remainder * percentile, 100L));
            long observed = 0L;
            for (int index = 0; index < bins.length; index++) {
                observed += bins[index];
                if (observed >= required) return index == 63 ? Long.MAX_VALUE : 1L << (index + 1);
            }
            throw new IllegalStateException("timing histogram is inconsistent");
        }

        private static int bin(long nanos) {
            if (nanos <= 1L) return 0;
            return Math.min(63, 63 - Long.numberOfLeadingZeros(nanos));
        }

        private static long saturatingAdd(long left, long right) {
            return left > Long.MAX_VALUE - right ? Long.MAX_VALUE : left + right;
        }
    }

    private static final class Queue {
        private long samples, currentLagTicks, maxLagTicks, observedAtTicks = -1;
        private int currentDepth, maxDepth;

        private void record(int depth, long lag, long observedAt) {
            samples = samples == Long.MAX_VALUE ? Long.MAX_VALUE : samples + 1L; currentDepth = depth; maxDepth = Math.max(maxDepth, depth);
            currentLagTicks = lag; maxLagTicks = Math.max(maxLagTicks, lag);
            observedAtTicks = observedAt;
        }

        private QueueSample snapshot(QueueKey key) {
            return new QueueSample(key.kind(), key.owner(), samples, currentDepth, maxDepth, currentLagTicks, maxLagTicks, observedAtTicks);
        }
    }
}

package io.farfrontier.palemirror.frontier.v3.kernel;

import io.farfrontier.palemirror.frontier.v3.api.CheckpointImage;
import io.farfrontier.palemirror.frontier.v3.api.EngineStatus;
import io.farfrontier.palemirror.frontier.v3.api.SimInstant;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldProjection;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.persistence.RecoveryImage;
import io.farfrontier.palemirror.frontier.v3.persistence.SnapshotRecord;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reproducible complete-domain pressure evidence for AUD-009/AUD-010.
 *
 * <p>This is deliberately a pure COLD route over the ordinary production bootstrap, not a
 * fixture that invents scenes or aggregate bodies. A one-action server-tick budget forces the
 * twelve same-instant field fronts through the real global order. The final segment then replays
 * its retained WAL from an ordinary checkpoint.</p>
 */
@Tag("scale-audit")
class FrontierV3ScaleAuditTest {
    private static final long SEED = 41L;
    private static final long TARGET_TICK = 100_000L;
    private static final long RECOVERY_SPLIT_TICK = 99_000L;
    private static final long COMPACTION_INTERVAL_REVISIONS = 1_024L;
    private static final WorkBudget ONE_ACTION_PER_TICK = new WorkBudget(1, 512);
    private static final int MINIMUM_EXACT_BODIES = 288;
    private static final long MAXIMUM_DEFERRED_LAG_TICKS = 256L;

    @Test
    void twelveSettlementPressureRetainsOneFairDeterministicOrderAndReplaysItsWal() {
        PressureRun first = run("first");
        PressureRun second = run("second");

        assertEquals(first.checkpoint(), second.checkpoint(), "the same production seed and tick budget retain one canonical result");
        assertEquals(first.schedulePlanOrder(), second.schedulePlanOrder(), "schedule service order may not depend on collection iteration");
        assertEquals(first.queueLags(), second.queueLags(), "deferred-work attribution must reproduce with the canonical order");
        assertTrue(first.initialBodies() >= MINIMUM_EXACT_BODIES,
                "the ordinary 12-settlement bootstrap must already carry hundreds of exact people/bioforms");
        assertTrue(first.finalBodies() > first.initialBodies(),
                "the pressure route must exercise normal exact-person growth rather than a fixed aggregate crowd");
        assertEquals(12, first.initialFrontOrder().size(), "all twelve independent resource-site fronts start at the same canonical instant");
        assertEquals(first.initialFrontOrder(), first.plannedFrontOrder(),
                "the constrained budget must eventually service every simultaneous front in its one stable total order");
        assertTrue(first.queueLags().values().stream().mapToLong(QueueLag::maxLagTicks).max().orElseThrow() <= MAXIMUM_DEFERRED_LAG_TICKS,
                "no deferred front may silently starve behind the global ordered queue");
        assertTrue(first.maxQueueDepth() <= 4_096, "the production future-work capacity is explicit and bounded");
        assertTrue(first.schedulePlanOrder().stream().anyMatch(value -> value.startsWith("frontier.hive_route_engagement.")),
                "the route must include a real hive interception front, not only settlement bookkeeping");
        assertTrue(first.schedulePlanOrder().stream().anyMatch(value -> value.startsWith("frontier.objective.assault|hive:frontier")),
                "the route must include a real hive settlement-assault decision");
        assertTrue(first.walReplayMatches(), "the retained post-checkpoint WAL must replay to the identical final checkpoint");
        System.out.printf(Locale.ROOT,
                "PMV3_SCALE_AUDIT seed=%d bodies=%d->%d schedulePlans=%d queueKeys=%d maxQueueDepth=%d maxLagTicks=%d hash=%s walReplay=%s%n",
                SEED, first.initialBodies(), first.finalBodies(), first.schedulePlanOrder().size(), first.queueLags().size(),
                first.maxQueueDepth(), maximumLag(first.queueLags()), first.canonicalDigest(), first.walReplayMatches());
    }

    private static PressureRun run(String run) {
        WorldId world = new WorldId("frontier:scale-audit-" + SEED);
        FrontierEngineConfiguration<FrontierWorldState, FrontierWorldProjection> configuration =
                FrontierWorldRuntimeDefinition.configuration(world, SEED);
        RecordingMetrics metrics = new RecordingMetrics();
        @SuppressWarnings("unchecked")
        InMemoryFrontierEngine<FrontierWorldState, FrontierWorldProjection> engine =
                (InMemoryFrontierEngine<FrontierWorldState, FrontierWorldProjection>) FrontierEngines.create(configuration.withExecutionMetrics(metrics));
        List<String> initialFrontOrder = configuration.initialSchedules().stream()
                .filter(action -> action.kind().equals("frontier.resource_site.prepare"))
                .sorted().map(FrontierV3ScaleAuditTest::attribution).toList();
        int initialBodies = exactBodies(configuration, engine.checkpoint());
        CheckpointImage recoveryCheckpoint = null;
        long compactedAt = 0L;
        for (long tick = 1L; tick <= TARGET_TICK; tick++) {
            var result = engine.advanceTo(new SimInstant(tick), ONE_ACTION_PER_TICK);
            long observedTick = tick;
            assertEquals(EngineStatus.Kind.ACTIVE, result.status().kind(),
                    () -> "pressure route " + run + " quarantined at tick=" + observedTick + " detail=" + result.status().failureDetail().orElse("none"));
            if (tick == RECOVERY_SPLIT_TICK) {
                recoveryCheckpoint = engine.checkpoint();
                engine.compact(recoveryCheckpoint.revision());
                compactedAt = recoveryCheckpoint.revision().value();
            } else if (tick < RECOVERY_SPLIT_TICK && result.revision().value() - compactedAt >= COMPACTION_INTERVAL_REVISIONS) {
                CheckpointImage compactionCheckpoint = engine.checkpoint();
                engine.compact(compactionCheckpoint.revision());
                compactedAt = compactionCheckpoint.revision().value();
            }
        }
        CheckpointImage finalCheckpoint = engine.checkpoint();
        InMemoryFrontierEngine<FrontierWorldState, FrontierWorldProjection> recovered = recover(configuration, recoveryCheckpoint, engine.transactions());
        List<String> plannedFrontOrder = metrics.schedulePlanOrder().stream()
                .filter(value -> value.startsWith("frontier.resource_site.prepare|"))
                .toList();
        return new PressureRun(initialBodies, exactBodies(configuration, finalCheckpoint), initialFrontOrder,
                metrics.schedulePlanOrder(), plannedFrontOrder, metrics.queueLags(), metrics.maxQueueDepth(), digest(finalCheckpoint), finalCheckpoint,
                finalCheckpoint.equals(recovered.checkpoint()));
    }

    @SuppressWarnings("unchecked")
    private static InMemoryFrontierEngine<FrontierWorldState, FrontierWorldProjection> recover(
            FrontierEngineConfiguration<FrontierWorldState, FrontierWorldProjection> configuration,
            CheckpointImage checkpoint, List<TransactionRecord> tail
    ) {
        assertTrue(checkpoint != null, "pressure route must retain its explicit recovery checkpoint");
        return (InMemoryFrontierEngine<FrontierWorldState, FrontierWorldProjection>) FrontierEngines.recover(configuration,
                new RecoveryImage(configuration.worldId(), Optional.of(new SnapshotRecord(checkpoint, checkpoint.revision().value())), tail));
    }

    private static int exactBodies(FrontierEngineConfiguration<FrontierWorldState, FrontierWorldProjection> configuration,
                                   CheckpointImage checkpoint) {
        return configuration.stateCodec().decode(checkpoint.canonicalState()).actorLocations().size();
    }

    private static String attribution(ScheduledAction action) { return action.kind() + "|" + action.subject().value(); }

    private static long maximumLag(Map<String, QueueLag> queueLags) {
        return queueLags.values().stream().mapToLong(QueueLag::maxLagTicks).max().orElse(0L);
    }

    private static String digest(CheckpointImage checkpoint) {
        try {
            MessageDigest hash = MessageDigest.getInstance("SHA-256");
            hash.update(checkpoint.canonicalState());
            checkpoint.schedules().forEach(action -> hash.update((action.id().value() + ":" + action.dueAt().ticks() + ":"
                    + action.priority() + ":" + attribution(action) + ":" + action.weight() + "\n").getBytes(StandardCharsets.UTF_8)));
            return java.util.HexFormat.of().formatHex(hash.digest());
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private record PressureRun(int initialBodies, int finalBodies, List<String> initialFrontOrder,
                               List<String> schedulePlanOrder, List<String> plannedFrontOrder, Map<String, QueueLag> queueLags, int maxQueueDepth,
                               String canonicalDigest, CheckpointImage checkpoint, boolean walReplayMatches) { }

    private record QueueLag(long samples, int maxDepth, long maxLagTicks) { }

    private static final class RecordingMetrics implements FrontierExecutionMetrics {
        private final List<String> schedulePlanOrder = new ArrayList<>();
        private final Map<String, MutableQueueLag> queues = new LinkedHashMap<>();
        private int maxQueueDepth;

        @Override
        public Span begin(Stage stage, String kind, String owner) {
            return () -> {
                if (stage == Stage.SCHEDULE_PLAN) schedulePlanOrder.add(kind + "|" + owner);
            };
        }

        @Override
        public void observeQueue(SimInstant instant, int queueDepth, Optional<ScheduledAction> deferred) {
            maxQueueDepth = Math.max(maxQueueDepth, queueDepth);
            deferred.ifPresent(action -> queues.computeIfAbsent(attribution(action), ignored -> new MutableQueueLag())
                    .record(queueDepth, Math.max(0L, instant.ticks() - action.dueAt().ticks())));
        }

        @Override public Snapshot snapshot() { return Snapshot.empty(); }

        List<String> schedulePlanOrder() { return List.copyOf(schedulePlanOrder); }

        Map<String, QueueLag> queueLags() {
            Map<String, QueueLag> result = new java.util.TreeMap<>();
            queues.forEach((key, value) -> result.put(key, value.snapshot()));
            return Map.copyOf(result);
        }

        int maxQueueDepth() { return maxQueueDepth; }
    }

    private static final class MutableQueueLag {
        private long samples;
        private int maxDepth;
        private long maxLagTicks;

        private void record(int depth, long lag) {
            samples++; maxDepth = Math.max(maxDepth, depth); maxLagTicks = Math.max(maxLagTicks, lag);
        }

        private QueueLag snapshot() { return new QueueLag(samples, maxDepth, maxLagTicks); }
    }
}

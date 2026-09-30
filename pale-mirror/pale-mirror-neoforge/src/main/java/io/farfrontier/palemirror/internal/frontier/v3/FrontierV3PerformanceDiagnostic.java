package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.CheckpointImage;
import io.farfrontier.palemirror.frontier.v3.kernel.FrontierExecutionMetrics;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.SceneLeaseStatus;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Read-only compact rendering of bounded v3 execution telemetry. */
final class FrontierV3PerformanceDiagnostic {
    /* Keep a useful pressure cut inside the shared 8 KiB operator response envelope even when
       every retained attribution uses its longest permitted identity. */
    private static final int MAX_STAGE_ROWS = 12;
    private static final int MAX_QUEUE_ROWS = 8;

    private FrontierV3PerformanceDiagnostic() { }

    static String render(CheckpointImage checkpoint, FrontierExecutionMetrics.Snapshot metrics) {
        return render(checkpoint, metrics, 0);
    }

    static String render(CheckpointImage checkpoint, FrontierExecutionMetrics.Snapshot metrics, int fastForwardRemaining) {
        return render(checkpoint, metrics, fastForwardRemaining, null, null, null);
    }

    /**
     * Includes the one bounded operator-time request as a read-only progress value.  A pilot
     * must not treat the command packet acknowledgement as completion while the server is still
     * advancing the canonical clock in slices.
     */
    static String render(CheckpointImage checkpoint, FrontierExecutionMetrics.Snapshot metrics, int fastForwardRemaining, Long fastForwardTarget,
                         String fastForwardFailure, FrontierV3ServerLifecycle.FastForwardTargetOutcome outcome) {
        return render(checkpoint, metrics, null, fastForwardRemaining, fastForwardTarget, fastForwardFailure, outcome, null);
    }

    /**
     * One bounded read-only pressure cut.  It counts retained canonical bindings rather than
     * scanning a level, loading a chunk, or claiming that a presentation entity is an actor.
     */
    static String render(CheckpointImage checkpoint, FrontierExecutionMetrics.Snapshot metrics, FrontierWorldState state,
                         int fastForwardRemaining, Long fastForwardTarget, String fastForwardFailure,
                         FrontierV3ServerLifecycle.FastForwardTargetOutcome outcome) {
        return render(checkpoint, metrics, state, fastForwardRemaining, fastForwardTarget, fastForwardFailure, outcome, null);
    }

    static String render(CheckpointImage checkpoint, FrontierExecutionMetrics.Snapshot metrics, FrontierWorldState state,
                         int fastForwardRemaining, Long fastForwardTarget, String fastForwardFailure,
                         FrontierV3ServerLifecycle.FastForwardTargetOutcome outcome,
                         FrontierV3ServerLifecycle.FastForwardSliceTelemetry sliceTelemetry) {
        return render(checkpoint, metrics, state, fastForwardRemaining, fastForwardTarget, fastForwardFailure, outcome, sliceTelemetry, List.of());
    }

    static String render(CheckpointImage checkpoint, FrontierExecutionMetrics.Snapshot metrics, FrontierWorldState state,
                         int fastForwardRemaining, Long fastForwardTarget, String fastForwardFailure,
                         FrontierV3ServerLifecycle.FastForwardTargetOutcome outcome,
                         FrontierV3ServerLifecycle.FastForwardSliceTelemetry sliceTelemetry,
                         List<FrontierV3ServerLifecycle.FastForwardRequestOutcome> requests) {
        if (fastForwardRemaining < 0 || fastForwardRemaining > FrontierV3ServerLifecycle.MAX_FAST_FORWARD_TICKS) {
            throw new IllegalArgumentException("bounded fast-forward remainder");
        }
        StringBuilder value = new StringBuilder("{\"schema\":1,\"kind\":\"performance\",\"id\":\"\",\"world\":\"")
                .append(quote(checkpoint.worldId().value())).append("\",\"revision\":")
                .append(checkpoint.revision().value()).append(",\"status\":\"ok\",\"droppedAttributions\":")
                .append(metrics.droppedAttributions()).append(",\"auditReadyWithoutWake\":")
                .append(metrics.auditReadyWithoutWake()).append(",\"stageCount\":").append(metrics.stages().size()).append(",\"queueCount\":")
                .append(metrics.queues().size()).append(",\"fastForwardRemaining\":").append(fastForwardRemaining)
                .append(",\"instant\":").append(checkpoint.instant().ticks())
                .append(",\"fastForwardTarget\":").append(fastForwardTarget == null ? "null" : fastForwardTarget)
                .append(",\"fastForwardTargetStatus\":\"").append(fastForwardFailure == null ? (fastForwardTarget == null ? "NONE" : (fastForwardRemaining == 0 ? "HELD" : "ADVANCING")) : "REJECTED")
                .append("\",\"fastForwardFailure\":").append(fastForwardFailure == null ? "null" : "\"" + quote(fastForwardFailure) + "\"")
                .append(",\"fastForwardTargetOutcome\":").append(outcome == null ? "null" : outcome(outcome))
                .append(",\"fastForwardRequests\":").append(requests(requests))
                .append(",\"fastForwardSlice\":").append(sliceTelemetry == null ? "null" : sliceTelemetry(sliceTelemetry))
                .append(",\"worstSpan\":").append(worstSpan(metrics.stages()))
                .append(",\"stages\":[");
        appendStages(value, metrics.stages()); value.append("],\"queues\":["); appendQueues(value, metrics.queues()); value.append(']');
        if (state != null) value.append(",\"frontier\":").append(frontier(state, checkpoint));
        return FrontierV3DiagnosticJson.bounded("performance", "", checkpoint, value.append('}').toString());
    }

    private static String frontier(FrontierWorldState state, CheckpointImage checkpoint) {
        long hotScenes = state.sceneLeases().values().stream().filter(lease -> lease.status() == SceneLeaseStatus.HOT).count();
        long activeScenes = state.sceneLeases().values().stream().filter(lease -> lease.status() == SceneLeaseStatus.PREPARED
                || lease.status() == SceneLeaseStatus.HOT || lease.status() == SceneLeaseStatus.DRAINING).count();
        Set<io.farfrontier.palemirror.frontier.v3.api.SubjectId> bindings = new HashSet<>();
        state.sceneLeases().values().stream().filter(lease -> lease.status() == SceneLeaseStatus.PREPARED || lease.status() == SceneLeaseStatus.HOT
                || lease.status() == SceneLeaseStatus.DRAINING).forEach(lease -> lease.members().forEach(member -> bindings.add(member.actorId())));
        long sceneActorBindings = bindings.size();
        long hotAmbient = state.ambientLeases().values().stream().filter(lease -> lease.status()
                == io.farfrontier.palemirror.frontier.v3.model.AmbientLeaseStatus.HOT).peek(lease -> bindings.add(lease.actorId())).count();
        long activeAssaults = state.strategicPlans().settlementAssaults().values().stream().filter(assault -> assault.status()
                != io.farfrontier.palemirror.frontier.v3.model.SettlementAssaultStatus.RESOLVED).count();
        long activeEngagements = state.strategicPlans().routeEngagements().values().stream().filter(engagement -> engagement.status()
                != io.farfrontier.palemirror.frontier.v3.model.RouteEngagementStatus.RESOLVED).count();
        long settlementAuthorities = state.strategicPlans().decisionAuthorities().authorities().values().stream().filter(authority -> authority.kind()
                == io.farfrontier.palemirror.frontier.v3.model.DecisionAuthorityKind.SETTLEMENT).count();
        long hivemindAuthorities = state.strategicPlans().decisionAuthorities().authorities().values().stream().filter(authority -> authority.kind()
                == io.farfrontier.palemirror.frontier.v3.model.DecisionAuthorityKind.HIVEMIND).count();
        return "{\"settlements\":" + state.bootstrap().settlements().size() + ",\"seedNests\":" + state.bootstrap().hive().seedNests().size()
                + ",\"settlementDecisionAuthorities\":" + settlementAuthorities + ",\"hivemindDecisionAuthorities\":" + hivemindAuthorities
                + ",\"activeSceneLeases\":" + activeScenes
                + ",\"hotSceneLeases\":" + hotScenes + ",\"sceneActorBindings\":" + sceneActorBindings + ",\"hotAmbientLeases\":" + hotAmbient + ",\"managedActorBindings\":" + bindings.size()
                + ",\"activeAssaults\":" + activeAssaults + ",\"activeRouteEngagements\":" + activeEngagements
                + ",\"physicalIntents\":" + state.physicalIntents().size() + ",\"physicalObservations\":" + state.physicalObservations().size()
                + ",\"deferredAftermath\":" + state.deferredAftermath().entries().size() + ",\"recoveryCurrent\":" + state.fencedRecovery().current().size()
                + ",\"recoveryTombstones\":" + state.fencedRecovery().tombstones().size() + ",\"checkpointBytes\":" + checkpoint.canonicalState().length + "}";
    }

    private static String outcome(FrontierV3ServerLifecycle.FastForwardTargetOutcome value) {
        return "{\"requestId\":" + value.requestId() + ",\"targetInstant\":" + value.targetInstant()
                + ",\"admittedCheckpointInstant\":" + (value.admittedCheckpointInstant() == null ? "null" : value.admittedCheckpointInstant())
                + ",\"reachedCheckpointInstant\":" + (value.reachedCheckpointInstant() == null ? "null" : value.reachedCheckpointInstant())
                + ",\"status\":\"" + value.status() + "\",\"failure\":"
                + (value.failure() == null ? "null" : "\"" + quote(value.failure()) + "\"") + "}";
    }

    private static String requests(List<FrontierV3ServerLifecycle.FastForwardRequestOutcome> values) {
        StringBuilder result = new StringBuilder("["); boolean first = true;
        for (FrontierV3ServerLifecycle.FastForwardRequestOutcome value : values) {
            if (!first) result.append(','); first = false;
            result.append("{\"requestId\":").append(value.requestId()).append(",\"kind\":\"").append(value.kind())
                    .append("\",\"requestedTicks\":").append(value.requestedTicks()).append(",\"targetInstant\":")
                    .append(value.targetInstant() == null ? "null" : value.targetInstant()).append(",\"admittedCheckpointInstant\":")
                    .append(value.admittedCheckpointInstant() == null ? "null" : value.admittedCheckpointInstant()).append(",\"reachedCheckpointInstant\":")
                    .append(value.reachedCheckpointInstant() == null ? "null" : value.reachedCheckpointInstant()).append(",\"status\":\"")
                    .append(value.status()).append("\",\"reason\":")
                    .append(value.reason() == null ? "null" : "\"" + quote(value.reason()) + "\"").append('}');
        }
        return result.append(']').toString();
    }

    private static String sliceTelemetry(FrontierV3ServerLifecycle.FastForwardSliceTelemetry value) {
        return "{\"samples\":" + value.samples() + ",\"advancedTicks\":" + value.advancedTicks()
                + ",\"totalNanos\":" + value.totalNanos() + ",\"maxNanos\":" + value.maxNanos()
                + ",\"safetyNanos\":" + value.safetyNanos() + ",\"maxSafetyNanos\":" + value.maxSafetyNanos()
                + ",\"advanceNanos\":" + value.advanceNanos() + ",\"maxAdvanceNanos\":" + value.maxAdvanceNanos() + "}";
    }

    private static String worstSpan(List<FrontierExecutionMetrics.StageSample> samples) {
        return samples.stream().max(Comparator.comparingLong(FrontierExecutionMetrics.StageSample::maxNanos)
                        .thenComparingLong(FrontierExecutionMetrics.StageSample::totalNanos)
                        .thenComparing(sample -> sample.stage().name()).thenComparing(FrontierExecutionMetrics.StageSample::kind)
                        .thenComparing(FrontierExecutionMetrics.StageSample::owner))
                .map(sample -> "{\"stage\":\"" + sample.stage() + "\",\"kind\":\"" + quote(sample.kind())
                        + "\",\"owner\":\"" + quote(sample.owner()) + "\",\"maxNanos\":" + sample.maxNanos() + "}")
                .orElse("null");
    }

    private static void appendStages(StringBuilder value, List<FrontierExecutionMetrics.StageSample> samples) {
        boolean first = true;
        for (FrontierExecutionMetrics.StageSample sample : samples.stream()
                .sorted(Comparator.comparingLong(FrontierExecutionMetrics.StageSample::totalNanos).reversed()
                        .thenComparing(sample -> sample.stage().name()).thenComparing(FrontierExecutionMetrics.StageSample::kind)
                        .thenComparing(FrontierExecutionMetrics.StageSample::owner)).limit(MAX_STAGE_ROWS).toList()) {
            if (!first) value.append(','); first = false;
            value.append("{\"stage\":\"").append(sample.stage()).append("\",\"kind\":\"").append(quote(sample.kind()))
                    .append("\",\"owner\":\"").append(quote(sample.owner())).append("\",\"samples\":").append(sample.samples())
                    .append(",\"totalNanos\":").append(sample.totalNanos()).append(",\"maxNanos\":").append(sample.maxNanos())
                    .append(",\"p50UpperNanos\":").append(sample.p50UpperNanos()).append(",\"p95UpperNanos\":")
                    .append(sample.p95UpperNanos()).append(",\"p99UpperNanos\":").append(sample.p99UpperNanos()).append('}');
        }
    }

    private static void appendQueues(StringBuilder value, List<FrontierExecutionMetrics.QueueSample> samples) {
        boolean first = true;
        for (FrontierExecutionMetrics.QueueSample sample : samples.stream()
                .sorted(Comparator.comparingLong(FrontierExecutionMetrics.QueueSample::maxLagTicks).reversed()
                        .thenComparing(Comparator.comparingInt(FrontierExecutionMetrics.QueueSample::maxDepth).reversed())
                        .thenComparing(FrontierExecutionMetrics.QueueSample::kind).thenComparing(FrontierExecutionMetrics.QueueSample::owner))
                .limit(MAX_QUEUE_ROWS).toList()) {
            if (!first) value.append(','); first = false;
            value.append("{\"kind\":\"").append(quote(sample.kind())).append("\",\"owner\":\"").append(quote(sample.owner()))
                    .append("\",\"samples\":").append(sample.samples()).append(",\"currentDepth\":").append(sample.currentDepth())
                    .append(",\"maxDepth\":").append(sample.maxDepth()).append(",\"currentLagTicks\":").append(sample.currentLagTicks())
                    .append(",\"maxLagTicks\":").append(sample.maxLagTicks()).append('}');
        }
    }

    private static String quote(String value) { return value.replace("\\", "\\\\").replace("\"", "\\\""); }
}

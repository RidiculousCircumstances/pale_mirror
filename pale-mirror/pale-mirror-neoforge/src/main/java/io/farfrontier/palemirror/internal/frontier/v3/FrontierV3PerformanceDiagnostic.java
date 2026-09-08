package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.CheckpointImage;
import io.farfrontier.palemirror.frontier.v3.kernel.FrontierExecutionMetrics;

import java.util.Comparator;
import java.util.List;

/** Read-only compact rendering of bounded v3 execution telemetry. */
final class FrontierV3PerformanceDiagnostic {
    private static final int MAX_STAGE_ROWS = 24;
    private static final int MAX_QUEUE_ROWS = 16;

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
        if (fastForwardRemaining < 0 || fastForwardRemaining > FrontierV3ServerLifecycle.MAX_FAST_FORWARD_TICKS) {
            throw new IllegalArgumentException("bounded fast-forward remainder");
        }
        StringBuilder value = new StringBuilder("{\"schema\":1,\"kind\":\"performance\",\"id\":\"\",\"world\":\"")
                .append(quote(checkpoint.worldId().value())).append("\",\"revision\":")
                .append(checkpoint.revision().value()).append(",\"status\":\"ok\",\"droppedAttributions\":")
                .append(metrics.droppedAttributions()).append(",\"stageCount\":").append(metrics.stages().size()).append(",\"queueCount\":")
                .append(metrics.queues().size()).append(",\"fastForwardRemaining\":").append(fastForwardRemaining)
                .append(",\"instant\":").append(checkpoint.instant().ticks())
                .append(",\"fastForwardTarget\":").append(fastForwardTarget == null ? "null" : fastForwardTarget)
                .append(",\"fastForwardTargetStatus\":\"").append(fastForwardFailure == null ? (fastForwardTarget == null ? "NONE" : (fastForwardRemaining == 0 ? "HELD" : "ADVANCING")) : "REJECTED")
                .append("\",\"fastForwardFailure\":").append(fastForwardFailure == null ? "null" : "\"" + quote(fastForwardFailure) + "\"")
                .append(",\"fastForwardTargetOutcome\":").append(outcome == null ? "null" : outcome(outcome))
                .append(",\"stages\":[");
        appendStages(value, metrics.stages()); value.append("],\"queues\":["); appendQueues(value, metrics.queues());
        return FrontierV3DiagnosticJson.bounded("performance", "", checkpoint, value.append("]}").toString());
    }

    private static String outcome(FrontierV3ServerLifecycle.FastForwardTargetOutcome value) {
        return "{\"requestId\":" + value.requestId() + ",\"targetInstant\":" + value.targetInstant()
                + ",\"admittedCheckpointInstant\":" + (value.admittedCheckpointInstant() == null ? "null" : value.admittedCheckpointInstant())
                + ",\"reachedCheckpointInstant\":" + (value.reachedCheckpointInstant() == null ? "null" : value.reachedCheckpointInstant())
                + ",\"status\":\"" + value.status() + "\",\"failure\":"
                + (value.failure() == null ? "null" : "\"" + quote(value.failure()) + "\"") + "}";
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

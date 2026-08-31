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
        StringBuilder value = new StringBuilder("{\"schema\":1,\"kind\":\"performance\",\"id\":\"\",\"revision\":")
                .append(checkpoint.revision().value()).append(",\"status\":\"ok\",\"droppedAttributions\":")
                .append(metrics.droppedAttributions()).append(",\"stageCount\":").append(metrics.stages().size()).append(",\"queueCount\":")
                .append(metrics.queues().size()).append(",\"stages\":[");
        appendStages(value, metrics.stages()); value.append("],\"queues\":["); appendQueues(value, metrics.queues());
        return FrontierV3DiagnosticJson.bounded("performance", "", checkpoint, value.append("]}").toString());
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

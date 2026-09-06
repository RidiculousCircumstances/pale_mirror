package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.CheckpointImage;

/** Read-only stable view of the physical execution plan; it has no world or canonical authority. */
final class FrontierV3PhysicalExecutionDiagnostic {
    private FrontierV3PhysicalExecutionDiagnostic() { }

    static String render(CheckpointImage checkpoint) {
        StringBuilder value = new StringBuilder("{\"schema\":1,\"kind\":\"execution\",\"id\":\"\",\"revision\":")
                .append(checkpoint.revision().value()).append(",\"status\":\"ok\",\"executors\":[");
        boolean first = true;
        for (FrontierV3PhysicalExecutorRegistry.Diagnostic executor : FrontierV3PhysicalExecutors.registry().diagnostics()) {
            if (!first) value.append(','); first = false;
            value.append("{\"id\":\"").append(quote(executor.id())).append("\",\"stage\":\"")
                    .append(executor.stage()).append("\",\"dependencies\":").append(strings(executor.dependencies()))
                    .append(",\"exclusiveWrites\":").append(strings(executor.exclusiveWrites()))
                    .append(",\"maxInvocationsPerTick\":").append(executor.maxInvocationsPerTick()).append('}');
        }
        return FrontierV3DiagnosticJson.bounded("execution", "", checkpoint, value.append("]}").toString());
    }

    private static String strings(java.util.List<String> values) {
        return values.stream().map(value -> "\"" + quote(value) + "\"").collect(java.util.stream.Collectors.joining(",", "[", "]"));
    }

    private static String quote(String value) { return value.replace("\\", "\\\\").replace("\"", "\\\""); }
}

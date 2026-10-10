package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.FrontierScheduleView;
import io.farfrontier.palemirror.frontier.v3.kernel.FrontierExecutionMetrics;

/** Read-only counter snapshot for the two retained projection owners; it has no scheduling authority. */
final class FrontierV3ProjectionWorkDiagnostic {
    private FrontierV3ProjectionWorkDiagnostic() { }

    static String render(FrontierScheduleView checkpoint, FrontierV3ServerRuntime<?, ?> runtime) {
        FrontierV3GrayboxExecutor.ProjectionWorkSnapshot structural = FrontierV3GrayboxExecutor.projectionWork(runtime);
        FrontierV3InfectionOverlayExecutor.ProjectionWorkSnapshot overlay = FrontierV3InfectionOverlayExecutor.projectionWork(runtime);
        FrontierExecutionMetrics.StageSample callerPath = runtime.executionMetrics().snapshot().stages().stream()
                .filter(sample -> sample.stage() == FrontierExecutionMetrics.Stage.PHYSICAL && sample.kind().equals("graybox-projection"))
                .findFirst().orElse(null);
        String value = "{\"schema\":1,\"kind\":\"projection_work\",\"id\":\"\",\"world\":\"" + checkpoint.worldId().value()
                + "\",\"revision\":" + checkpoint.revision().value() + ",\"instant\":" + checkpoint.instant().ticks() + ",\"status\":\"ok\""
                + ",\"structural\":{\"compatibilityChecks\":" + structural.compatibilityChecks() + ",\"freshnessConstructions\":"
                + structural.freshnessConstructions() + ",\"planCompilations\":" + structural.planCompilations() + ",\"providerAcquisitions\":"
                + structural.providerAcquisitions() + ",\"pointQueries\":" + structural.pointQueries() + ",\"lastTrigger\":\""
                + structural.lastTrigger() + "\",\"lastDisposition\":\"" + structural.lastDisposition()
                + "\",\"lastCompileTrigger\":\"" + structural.lastCompileTrigger() + "\",\"subpaths\":{"
                + "\"cursor\":" + cost(structural.cursorPath()) + ",\"firstVisibility\":" + cost(structural.firstVisibilityPath())
                + ",\"stagingRetirement\":" + cost(structural.stagingRetirementPath()) + ",\"deferredProjection\":" + cost(structural.deferredProjectionPath()) + "}}"
                + ",\"callerPath\":" + callerPath(callerPath)
                + ",\"infectionOverlay\":{\"compatibilityChecks\":" + overlay.compatibilityChecks() + ",\"freshnessConstructions\":"
                + overlay.freshnessConstructions() + ",\"planCompilations\":" + overlay.planCompilations() + "}}";
        return FrontierV3DiagnosticJson.bounded("projection_work", "", checkpoint, value);
    }

    private static String callerPath(FrontierExecutionMetrics.StageSample sample) {
        if (sample == null) return "{\"status\":\"NOT_RECORDED\"}";
        return "{\"status\":\"RECORDED\",\"owner\":\"" + sample.owner() + "\",\"samples\":" + sample.samples()
                + ",\"totalNanos\":" + sample.totalNanos() + ",\"maxNanos\":" + sample.maxNanos()
                + ",\"p99UpperNanos\":" + sample.p99UpperNanos() + "}";
    }

    private static String cost(FrontierV3GrayboxExecutor.ProjectionCost cost) {
        return "{\"calls\":" + cost.calls() + ",\"totalNanos\":" + cost.totalNanos() + ",\"maxNanos\":" + cost.maxNanos() + "}";
    }
}

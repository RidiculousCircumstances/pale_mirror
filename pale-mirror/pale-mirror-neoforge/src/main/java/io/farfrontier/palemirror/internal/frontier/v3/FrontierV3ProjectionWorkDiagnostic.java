package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.CheckpointImage;

/** Read-only counter snapshot for the two retained projection owners; it has no scheduling authority. */
final class FrontierV3ProjectionWorkDiagnostic {
    private FrontierV3ProjectionWorkDiagnostic() { }

    static String render(CheckpointImage checkpoint, FrontierV3ServerRuntime<?, ?> runtime) {
        FrontierV3GrayboxExecutor.ProjectionWorkSnapshot structural = FrontierV3GrayboxExecutor.projectionWork(runtime);
        FrontierV3InfectionOverlayExecutor.ProjectionWorkSnapshot overlay = FrontierV3InfectionOverlayExecutor.projectionWork(runtime);
        String value = "{\"schema\":1,\"kind\":\"projection_work\",\"id\":\"\",\"world\":\"" + checkpoint.worldId().value()
                + "\",\"revision\":" + checkpoint.revision().value() + ",\"instant\":" + checkpoint.instant().ticks() + ",\"status\":\"ok\""
                + ",\"structural\":{\"compatibilityChecks\":" + structural.compatibilityChecks() + ",\"freshnessConstructions\":"
                + structural.freshnessConstructions() + ",\"planCompilations\":" + structural.planCompilations() + ",\"providerAcquisitions\":"
                + structural.providerAcquisitions() + ",\"pointQueries\":" + structural.pointQueries() + "}"
                + ",\"infectionOverlay\":{\"compatibilityChecks\":" + overlay.compatibilityChecks() + ",\"freshnessConstructions\":"
                + overlay.freshnessConstructions() + ",\"planCompilations\":" + overlay.planCompilations() + "}}";
        return FrontierV3DiagnosticJson.bounded("projection_work", "", checkpoint, value);
    }
}

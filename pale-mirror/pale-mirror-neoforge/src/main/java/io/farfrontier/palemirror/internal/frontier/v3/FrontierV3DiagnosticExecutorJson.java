package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.CheckpointImage;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.FrontierSceneBehaviors;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.SceneLease;
import io.farfrontier.palemirror.frontier.v3.model.SceneLeaseStatus;
import java.util.Comparator;
import java.util.Optional;

/** Renders read-only executor/readiness and causal-trace diagnostics. */
final class FrontierV3DiagnosticExecutorJson {
    private FrontierV3DiagnosticExecutorJson() { }

    static String equipmentIssueReadiness(FrontierV3EquipmentIssueExecutor.Readiness value) {
        return ",\"physicalReadiness\":{\"selection\":\"" + FrontierV3DiagnosticJson.quote(value.selection().name())
                + "\",\"detail\":\"" + FrontierV3DiagnosticJson.quote(value.detail()) + "\"}";
    }

    static String equipmentReturnReadiness(FrontierV3EquipmentReturnExecutor.Readiness value) {
        return ",\"physicalReadiness\":{\"selection\":\"" + FrontierV3DiagnosticJson.quote(value.selection().name())
                + "\",\"detail\":\"" + FrontierV3DiagnosticJson.quote(value.detail()) + "\"}";
    }

    static String harvestReadiness(FrontierV3ResourceSiteHarvestExecutor.Readiness value) {
        return ",\"physicalReadiness\":{\"fieldLoaded\":" + value.fieldLoaded()
                + ",\"depotLoaded\":" + value.depotLoaded()
                + ",\"depotSurface\":\"" + FrontierV3DiagnosticJson.quote(value.depotSurface())
                + "\",\"ownedChestPresent\":" + value.ownedChestPresent()
                + ",\"fieldMatchesMatureStage\":" + value.fieldMatchesMatureStage()
                + ",\"outputSlotEmpty\":" + value.outputSlotEmpty()
                + ",\"claimedFieldStage\":" + value.claimedFieldStage()
                + ",\"fieldMatchesClaimedStage\":" + value.fieldMatchesClaimedStage()
                + ",\"precondition\":\"" + value.precondition()
                + "\",\"queued\":" + value.queued()
                + ",\"executionEligible\":" + value.executionEligible() + "}";
    }

    /** Selects a current lease for every diagnostic view without granting any lease authority. */
    static SceneLease currentLease(FrontierWorldState state, SubjectId sceneSubject) {
        return state.sceneLeases().values().stream()
                .filter(lease -> FrontierSceneBehaviors.owns(lease, sceneSubject))
                .max(Comparator.comparingInt((SceneLease lease) -> lease.status() == SceneLeaseStatus.CLOSED ? 0 : 1)
                        .thenComparing(SceneLease::handoffInstant)
                        .thenComparingLong(SceneLease::revision)
                        .thenComparing(SceneLease::id))
                .orElse(null);
    }

    static String trace(String id, CheckpointImage checkpoint, FrontierWorldState state, Optional<FrontierV3DiagnosticTrace.Entry> trace) {
        if (trace.isEmpty()) {
            var retainedHarvest = io.farfrontier.palemirror.frontier.v3.model.ResourceSiteHarvestTrace.lookup(state, id);
            if (retainedHarvest.isPresent()) {
                var value = retainedHarvest.orElseThrow();
                return FrontierV3DiagnosticJson.base("trace", id, checkpoint) + ",\"status\":\"retained\",\"correlation\":\""
                        + FrontierV3DiagnosticJson.quote(value.correlation()) + "\",\"driver\":\"" + FrontierV3DiagnosticJson.quote(value.driver())
                        + "\",\"cold\":{\"command\":\"" + FrontierV3DiagnosticJson.quote(value.coldCommandId()) + "\",\"event\":\""
                        + FrontierV3DiagnosticJson.quote(value.coldEventId()) + "\",\"revision\":" + value.coldRevision() + ",\"instant\":" + value.coldInstant()
                        + "},\"observation\":{\"id\":\"" + FrontierV3DiagnosticJson.quote(value.observationId()) + "\",\"command\":\""
                        + FrontierV3DiagnosticJson.quote(value.observationCommandId()) + "\",\"event\":\"" + FrontierV3DiagnosticJson.quote(value.observationEventId())
                        + "\",\"revision\":" + value.observationRevision() + ",\"instant\":" + value.observationInstant()
                        + "},\"complete\":" + value.complete() + "}";
            }
            String incidentId = id.startsWith("conflict:") ? id.substring("conflict:".length()) : id;
            var incident = state.diagnosticIncidents().incident(incidentId);
            if (incident.isEmpty()) return FrontierV3DiagnosticJson.unavailable("trace", id, checkpoint, "not_found");
            var value = incident.orElseThrow();
            return FrontierV3DiagnosticJson.base("trace", id, checkpoint) + ",\"status\":\"trace_incomplete\",\"correlation\":\""
                    + FrontierV3DiagnosticJson.quote(id) + "\",\"reason\":\"" + FrontierV3DiagnosticJson.quote(value.diagnostic().reason().name())
                    + "\",\"category\":\"" + value.diagnostic().category() + "\",\"owner\":\"" + FrontierV3DiagnosticJson.quote(value.diagnostic().owner().id().value())
                    + "\",\"subject\":\"" + FrontierV3DiagnosticJson.quote(value.diagnostic().subject().id().value()) + "\",\"disposition\":\""
                    + FrontierV3DiagnosticJson.quote(value.diagnostic().disposition().name()) + "\",\"firstCause\":\"" + FrontierV3DiagnosticJson.quote(value.firstCauseId())
                    + "\",\"history\":\"compacted_or_restart_local\"}";
        }
        FrontierV3DiagnosticTrace.Entry entry = trace.orElseThrow();
        return FrontierV3DiagnosticJson.base("trace", id, checkpoint) + ",\"status\":\"ok\",\"correlation\":\""
                + FrontierV3DiagnosticJson.quote(entry.correlation()) + "\",\"eventKind\":\""
                + FrontierV3DiagnosticJson.quote(entry.kind()) + "\",\"subject\":\""
                + FrontierV3DiagnosticJson.quote(entry.subject()) + "\",\"command\":\""
                + FrontierV3DiagnosticJson.quote(entry.commandId()) + "\",\"transaction\":\""
                + FrontierV3DiagnosticJson.quote(entry.transactionId()) + "\",\"acceptedRevision\":" + entry.revision()
                + ",\"chain\":" + FrontierV3DiagnosticJson.strings(entry.lineage()) + traceContext(entry.context()) + "}";
    }

    private static String traceContext(FrontierV3DiagnosticTrace.Context context) {
        if (context.operationId().isEmpty() && context.leaseId().isEmpty() && context.cargoId().isEmpty() && context.actorIds().isEmpty()) return "";
        return ",\"causal\":{\"operation\":\"" + FrontierV3DiagnosticJson.quote(context.operationId()) + "\",\"lease\":\""
                + FrontierV3DiagnosticJson.quote(context.leaseId()) + "\",\"cargo\":\""
                + FrontierV3DiagnosticJson.quote(context.cargoId()) + "\",\"actors\":"
                + FrontierV3DiagnosticJson.strings(context.actorIds()) + "}";
    }
}

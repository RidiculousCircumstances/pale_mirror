package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.CheckpointImage;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.ActorLocation;
import io.farfrontier.palemirror.frontier.v3.model.FrontierSceneLabels;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.ProductionJob;
import io.farfrontier.palemirror.frontier.v3.model.SceneLease;
import io.farfrontier.palemirror.frontier.v3.model.SceneLeaseStatus;

/** Read-only bounded production-process diagnostic; canonical ownership remains in Frontier state. */
final class FrontierV3ProductionProcessDiagnosticJson {
    private FrontierV3ProductionProcessDiagnosticJson() { }

    static String render(CheckpointImage checkpoint, FrontierWorldState state, ProductionJob job) {
        ActorLocation actor = state.actorLocations().get(job.workerId());
        SceneLease lease = currentLease(state, job.id());
        PhysicalIntent intent = state.physicalIntents().values().stream().filter(value -> value.causeSubjectId().equals(job.id()))
                .max(java.util.Comparator.comparing(PhysicalIntent::id)).orElse(null);
        var schedules = checkpoint.schedules().stream().filter(value -> value.subject().equals(job.id())).sorted().limit(4).toList();
        String scheduleEntries = schedules.stream().map(value -> "{\"id\":\"" + FrontierV3DiagnosticJson.quote(value.id().value())
                + "\",\"dueAt\":" + value.dueAt().ticks() + ",\"kind\":\"" + FrontierV3DiagnosticJson.quote(value.kind())
                + "\",\"weight\":" + value.weight() + "}").reduce((left, right) -> left + "," + right)
                .map(value -> "[" + value + "]").orElse("[]");
        String leaseValue = lease == null || lease.status() == SceneLeaseStatus.CLOSED ? "null"
                : "{\"id\":\"" + FrontierV3DiagnosticJson.quote(lease.id().value()) + "\",\"status\":\"" + lease.status()
                + "\",\"revision\":" + lease.revision() + ",\"members\":" + lease.members().size() + ",\"body\":"
                + (lease.memberPosition(job.workerId()) == null ? "null" : FrontierV3DiagnosticJson.position(lease.memberPosition(job.workerId()))) + "}";
        int cursorLength = job.workTraversal().linearCorridorSurfaces().size();
        return FrontierV3DiagnosticJson.base("process", job.id().value(), checkpoint) + ",\"status\":\"ok\",\"family\":\"frontier.production-work\""
                + ",\"identity\":{\"job\":\"" + FrontierV3DiagnosticJson.quote(job.id().value()) + "\",\"worker\":\"" + FrontierV3DiagnosticJson.quote(job.workerId().value())
                + "\",\"workerPresentation\":\"" + FrontierV3DiagnosticJson.quote(FrontierSceneLabels.actor(state, job.workerId(), false))
                + "\",\"outputItem\":\"" + FrontierV3DiagnosticJson.quote(job.outputItemId().value()) + "\"}"
                + ",\"claims\":{\"settlement\":\"" + FrontierV3DiagnosticJson.quote(job.settlementId().value()) + "\",\"facility\":\"" + FrontierV3DiagnosticJson.quote(job.facilityId().value())
                + "\",\"worker\":\"" + FrontierV3DiagnosticJson.quote(job.workerId().value()) + "\",\"inputItem\":\"" + FrontierV3DiagnosticJson.quote(job.consumedItemId().value())
                + "\",\"outputItem\":\"" + FrontierV3DiagnosticJson.quote(job.outputItemId().value()) + "\",\"lease\":" + leaseValue + "}"
                + ",\"schedule\":{\"count\":" + checkpoint.schedules().stream().filter(value -> value.subject().equals(job.id())).count() + ",\"entries\":" + scheduleEntries + "}"
                + ",\"cursor\":{\"index\":" + job.traversalCursor() + ",\"length\":" + cursorLength + ",\"retainedBody\":"
                + FrontierV3DiagnosticJson.position(job.workTraversal().linearCorridorSurfaces().get(job.traversalCursor()).standingBody())
                + ",\"actorBody\":" + (actor == null ? "null" : FrontierV3DiagnosticJson.position(actor.body())) + "}"
                + ",\"result\":{\"workStage\":\"" + job.workProgress().stage() + "\",\"completedTicks\":" + job.workProgress().completedTicks()
                + ",\"terminalEffectEligible\":" + job.workProgress().terminalEffectEligible() + ",\"inputHold\":\""
                + FrontierV3DiagnosticJson.quote(job.inputHold().getClass().getSimpleName()) + "\",\"intentKind\":\""
                + (intent == null ? "NONE" : intent.kind().name()) + "\",\"intentStatus\":\"" + (intent == null ? "NONE" : intent.status().name()) + "\"}}";
    }

    private static SceneLease currentLease(FrontierWorldState state, SubjectId subject) {
        return state.sceneLeases().values().stream().filter(lease -> io.farfrontier.palemirror.frontier.v3.model.FrontierSceneBehaviors.owns(lease, subject))
                .max(java.util.Comparator.comparingInt((SceneLease lease) -> lease.status() == SceneLeaseStatus.CLOSED ? 0 : 1)
                        .thenComparing(SceneLease::handoffInstant).thenComparingLong(SceneLease::revision).thenComparing(SceneLease::id)).orElse(null);
    }
}

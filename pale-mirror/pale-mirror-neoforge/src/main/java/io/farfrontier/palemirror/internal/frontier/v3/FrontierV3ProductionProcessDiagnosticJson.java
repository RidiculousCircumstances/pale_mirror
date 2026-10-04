package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.CheckpointImage;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.ActorLocation;
import io.farfrontier.palemirror.frontier.v3.model.BakeryWorkGoal;
import io.farfrontier.palemirror.frontier.v3.model.FrontierSceneLabels;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.ProductionJob;
import io.farfrontier.palemirror.frontier.v3.model.ReferenceContainerCustody;
import io.farfrontier.palemirror.frontier.v3.model.SceneLease;
import io.farfrontier.palemirror.frontier.v3.model.SceneLeaseStatus;
import io.farfrontier.palemirror.frontier.v3.process.BakeryProcess;

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
                + (lease.memberBody(state.actorLocations(), job.workerId()) == null ? "null" : FrontierV3DiagnosticJson.position(lease.memberBody(state.actorLocations(), job.workerId()))) + "}";
        int cursorLength = job.workTraversal().linearCorridorSurfaces().size();
        String progress = job.bakeryWork().map(work -> {
            BakeryWorkGoal goal = BakeryWorkGoal.current(state, job);
            var station = state.inventory().containers().values().stream().flatMap(container -> container.productionStation().stream())
                    .filter(value -> value.id().equals(work.stationId())).findFirst().orElseThrow();
            var stationScope = state.replicaCustody().custodyByScope().get(ReferenceContainerCustody.scopeId(station.containerId()));
            var stationReplica = state.replicaCustody().replicas().get(station.containerId());
            var depotScope = state.replicaCustody().custodyByScope().get(ReferenceContainerCustody.scopeId(
                    FrontierWorldState.depotId(job.settlementId())));
            long stationBindings = state.inventory().fungibleResources().bindings().values().stream()
                    .filter(value -> value.accountId().equals(work.stationAccountId())).count();
            return ",\"cursor\":null,\"goal\":{\"kind\":\"" + work.phase()
                    + "\",\"station\":" + (work.phase() == io.farfrontier.palemirror.frontier.v3.model.BakeryWorkState.Phase.DELIVERED
                        ? "null" : FrontierV3DiagnosticJson.position(goal.station().standingBody()))
                    + ",\"clearServiceAccess\":" + (work.phase() == io.farfrontier.palemirror.frontier.v3.model.BakeryWorkState.Phase.DELIVERED)
                    + "},\"result\":{\"workStage\":\"" + work.phase()
                    + "\",\"completedTicks\":" + work.completedWorkTicks()
                    + ",\"terminalEffectEligible\":" + (work.phase() == io.farfrontier.palemirror.frontier.v3.model.BakeryWorkState.Phase.DELIVERED)
                    + ",\"pendingPhysicalEffect\":" + work.pendingPhysicalStep().isPresent()
                    + ",\"localBlock\":" + work.block().map(block -> "{\"reason\":\"" + block.reason()
                    + "\",\"scope\":\"" + FrontierV3DiagnosticJson.quote(block.scopeId().value())
                    + "\",\"slot\":" + block.slot() + ",\"observedKind\":\""
                    + FrontierV3DiagnosticJson.quote(block.observedKind()) + "\",\"observedCount\":"
                    + block.observedCount() + "}").orElse("null")
                    + ",\"stationId\":\"" + FrontierV3DiagnosticJson.quote(work.stationId().value())
                    + "\",\"stationCustodyStatus\":\"" + (stationScope == null ? "NONE" : stationScope.status())
                    + "\",\"stationReplicaState\":\"" + (stationReplica == null ? "NONE" : stationReplica.state())
                    + "\",\"stationBoundStacks\":" + stationBindings
                    + ",\"depotCustodyStatus\":\"" + (depotScope == null ? "NONE" : depotScope.status()) + "\""
                    + ",\"coldBlocker\":\"" + BakeryProcess.coldBlocker(state, job).orElse("NONE") + "\""
                    + ",\"coldRouteBlocker\":\"" + FrontierV3DiagnosticJson.quote(
                    BakeryProcess.coldRouteBlocker(state, job).orElse("NONE")) + "\""
                    + ",\"actorBody\":" + (actor == null ? "null" : FrontierV3DiagnosticJson.position(actor.body()));
        }).orElseGet(() -> ",\"cursor\":{\"index\":" + job.traversalCursor() + ",\"length\":" + cursorLength + ",\"retainedBody\":"
                + FrontierV3DiagnosticJson.position(job.workTraversal().linearCorridorSurfaces().get(job.traversalCursor()).standingBody())
                + ",\"actorBody\":" + (actor == null ? "null" : FrontierV3DiagnosticJson.position(actor.body()))
                + ",\"approach\":" + FrontierV3StationApproachDiagnosticJson.write(job.spatial(),
                        job.workTraversal().linearCorridorSurfaces().get(job.traversalCursor()),
                        io.farfrontier.palemirror.frontier.v3.model.ProductionJourneyKnowledge.target(job)) + "}"
                + ",\"result\":{\"workStage\":\"" + job.workProgress().stage() + "\",\"completedTicks\":" + job.workProgress().completedTicks()
                + ",\"terminalEffectEligible\":" + job.workProgress().terminalEffectEligible());
        return FrontierV3DiagnosticJson.base("process", job.id().value(), checkpoint) + ",\"status\":\"ok\",\"family\":\"frontier.production-work\""
                + ",\"identity\":{\"job\":\"" + FrontierV3DiagnosticJson.quote(job.id().value()) + "\",\"worker\":\"" + FrontierV3DiagnosticJson.quote(job.workerId().value())
                + "\",\"workerPresentation\":\"" + FrontierV3DiagnosticJson.quote(FrontierSceneLabels.actor(state, job.workerId(), false))
                + "\",\"outputItem\":\"" + FrontierV3DiagnosticJson.quote(job.outputItemId().value()) + "\"}"
                + ",\"claims\":{\"settlement\":\"" + FrontierV3DiagnosticJson.quote(job.settlementId().value()) + "\",\"facility\":\"" + FrontierV3DiagnosticJson.quote(job.facilityId().value())
                + "\",\"worker\":\"" + FrontierV3DiagnosticJson.quote(job.workerId().value()) + "\",\"inputItem\":\"" + FrontierV3DiagnosticJson.quote(job.consumedItemId().value())
                + "\",\"outputItem\":\"" + FrontierV3DiagnosticJson.quote(job.outputItemId().value()) + "\",\"lease\":" + leaseValue + "}"
                + ",\"schedule\":{\"count\":" + checkpoint.schedules().stream().filter(value -> value.subject().equals(job.id())).count() + ",\"entries\":" + scheduleEntries + "}"
                + progress + ",\"inputHold\":\""
                + FrontierV3DiagnosticJson.quote(job.inputHold().getClass().getSimpleName()) + "\",\"intentKind\":\""
                + (intent == null ? "NONE" : intent.kind().name()) + "\",\"intentStatus\":\"" + (intent == null ? "NONE" : intent.status().name()) + "\"}}";
    }

    private static SceneLease currentLease(FrontierWorldState state, SubjectId subject) {
        return state.sceneLeases().values().stream().filter(lease -> io.farfrontier.palemirror.frontier.v3.model.FrontierSceneBehaviors.owns(lease, subject))
                .max(java.util.Comparator.comparingInt((SceneLease lease) -> lease.status() == SceneLeaseStatus.CLOSED ? 0 : 1)
                        .thenComparing(SceneLease::handoffInstant).thenComparingLong(SceneLease::revision).thenComparing(SceneLease::id)).orElse(null);
    }
}

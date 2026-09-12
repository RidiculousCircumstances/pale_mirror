package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.CheckpointImage;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.FrontierSceneBehaviors;
import io.farfrontier.palemirror.frontier.v3.model.FrontierRoutePatrolSceneSupport;
import io.farfrontier.palemirror.frontier.v3.model.FrontierSettlementServiceWorkSceneSupport;
import io.farfrontier.palemirror.frontier.v3.model.LogisticsSceneCause;
import io.farfrontier.palemirror.frontier.v3.model.MedicalTreatmentSceneCause;
import io.farfrontier.palemirror.frontier.v3.model.ProductionJob;
import io.farfrontier.palemirror.frontier.v3.model.SceneLease;
import io.farfrontier.palemirror.frontier.v3.model.SceneLeaseStatus;
import io.farfrontier.palemirror.frontier.v3.model.SceneStrikeObservation;
import io.farfrontier.palemirror.frontier.v3.model.SettlementAssault;
import io.farfrontier.palemirror.frontier.v3.model.SettlementAssaultCauseIdentity;
import io.farfrontier.palemirror.frontier.v3.model.SettlementAssaultSceneCause;
import io.farfrontier.palemirror.frontier.v3.model.SettlementAssaultStatus;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;

import java.util.Optional;

/** Owns the bounded typed scene projection, including the exact HOT strike receipt fields. */
final class FrontierV3SceneDiagnosticJson {
    private FrontierV3SceneDiagnosticJson() { }

    static String render(String id, CheckpointImage checkpoint, FrontierWorldState state,
                         Optional<FrontierV3SceneReadiness.Value> readiness) {
        SubjectId sceneSubject;
        try { sceneSubject = new SubjectId(id); }
        catch (IllegalArgumentException invalid) { return FrontierV3DiagnosticJson.unavailable("scene", id, checkpoint, "not_found"); }
        SceneLease lease = currentLease(state, sceneSubject);
        if (lease == null) return FrontierV3DiagnosticJson.unavailable("scene", id, checkpoint, "not_found");
        LogisticsSceneCause logistics = FrontierSceneBehaviors.isLogistics(lease) ? FrontierSceneBehaviors.logistics(lease) : null;
        SettlementAssaultSceneCause assault = FrontierSceneBehaviors.isSettlementAssault(lease) ? FrontierSceneBehaviors.settlementAssault(lease) : null;
        var engineering = FrontierSceneBehaviors.isEngineeringWorksite(lease) ? FrontierSceneBehaviors.engineeringWorksite(lease) : null;
        MedicalTreatmentSceneCause medical = FrontierSceneBehaviors.isMedicalTreatment(lease) ? FrontierSceneBehaviors.medicalTreatment(lease) : null;
        var harvest = FrontierSceneBehaviors.isResourceSiteHarvest(lease) ? FrontierSceneBehaviors.resourceSiteHarvest(lease) : null;
        var production = FrontierSceneBehaviors.isProductionWork(lease) ? FrontierSceneBehaviors.productionWork(lease) : null;
        var service = FrontierSceneBehaviors.isServiceWork(lease) ? FrontierSceneBehaviors.serviceWork(lease) : null;
        var routePatrol = FrontierSceneBehaviors.isRoutePatrol(lease) ? FrontierSceneBehaviors.routePatrol(lease) : null;
        ProductionJob productionJob = production == null ? null : state.productionJobs().get(production.jobId());
        var serviceWork = service == null ? null : state.serviceWorks().get(service.workId());
        var patrol = routePatrol == null ? null : state.strategicPlans().routePatrols().get(routePatrol.taskId());
        SubjectId engagement = logistics == null ? null : logistics.engagementId().orElse(null);
        var primaryMember = lease.members().getFirst();
        PhysicalIntent explosion = state.physicalIntents().values().stream().filter(value -> value.kind() == io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind.EXPLOSION)
                .filter(value -> engagement != null && value.subjectIds().size() == 2 && value.subjectIds().getLast().equals(engagement))
                .sorted(java.util.Comparator.comparing(PhysicalIntent::id)).findFirst().orElse(null);
        SubjectId strikeCause = logistics != null ? logistics.operationId() : assault != null ? assault.assaultId() : null;
        SettlementAssault assaultState = assault == null ? null : state.strategicPlans().settlementAssaults().get(assault.assaultId());
        PhysicalIntent strike = strikeCause == null ? null : logistics != null ? state.physicalIntents().values().stream()
                .filter(value -> value.kind() == io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind.SCENE_STRIKE)
                .filter(value -> value.causeSubjectId().equals(strikeCause)).sorted(java.util.Comparator.comparing(PhysicalIntent::id)).findFirst().orElse(null)
                : currentAssaultStrike(state, assaultState);
        long strikeEpoch = strike == null || assaultState == null ? -1L : SettlementAssaultCauseIdentity.epoch(assaultState.id(), strike.causeSubjectId());
        SceneStrikeObservation receipt = strike == null ? null : strike.postconditionObservationId().map(state.physicalObservations()::get)
                .filter(SceneStrikeObservation.class::isInstance).map(SceneStrikeObservation.class::cast).orElse(null);
        boolean exactReceipt = receipt != null && receipt.intentId().equals(strike.id()) && receipt.attackerId().equals(strike.subjectIds().getFirst())
                && receipt.targetId().equals(strike.subjectIds().getLast());
        boolean healthChanged = exactReceipt && receipt.targetHealthAfter().compareTo(receipt.targetHealthBefore()) < 0;
        String recovery = lease.recoveryEvidence().map(value -> ",\"recoveryMissingActors\":" + FrontierV3DiagnosticJson.strings(value.missingActorIds().stream().map(SubjectId::value).sorted().toList())
                + ",\"recoveryMissingCarrier\":" + value.missingCargoCarrier()).orElse("");
        return FrontierV3DiagnosticJson.base("scene", id, checkpoint) + ",\"status\":\"ok\",\"leaseId\":\"" + FrontierV3DiagnosticJson.quote(lease.id().value())
                + "\",\"leaseStatus\":\"" + lease.status() + "\",\"sceneKind\":\"" + (logistics != null ? "LOGISTICS" : assault != null ? "SETTLEMENT_ASSAULT"
                : engineering != null ? "ENGINEERING_WORKSITE" : medical != null ? "MEDICAL_TREATMENT" : harvest != null ? "RESOURCE_SITE_HARVEST"
                : production != null ? "PRODUCTION_WORK" : service != null ? "SETTLEMENT_SERVICE_WORK" : routePatrol != null ? "ROUTE_PATROL" : "UNKNOWN")
                + "\",\"operation\":\"" + FrontierV3DiagnosticJson.quote(logistics == null ? "" : logistics.operationId().value())
                + "\",\"assault\":\"" + FrontierV3DiagnosticJson.quote(assault == null ? "" : assault.assaultId().value())
                + "\",\"project\":\"" + FrontierV3DiagnosticJson.quote(engineering == null ? "" : engineering.projectId().value())
                + "\",\"medical\":\"" + FrontierV3DiagnosticJson.quote(medical == null ? "" : medical.operationId().value())
                + "\",\"harvestJob\":\"" + FrontierV3DiagnosticJson.quote(harvest == null ? "" : harvest.jobId().value())
                + "\",\"productionJob\":\"" + FrontierV3DiagnosticJson.quote(production == null ? "" : production.jobId().value())
                + "\",\"patrolTask\":\"" + FrontierV3DiagnosticJson.quote(routePatrol == null ? "" : routePatrol.taskId().value())
                + "\"" + productionTraversal(productionJob) + serviceTraversal(serviceWork) + patrolTraversal(patrol)
                + ",\"members\":" + lease.members().size() + ",\"primaryActor\":\"" + FrontierV3DiagnosticJson.quote(primaryMember.actorId().value())
                + "\",\"primaryEntityUuid\":\"" + primaryMember.entityId() + "\",\"explosionStatus\":\"" + (explosion == null ? "NONE" : explosion.status()) + "\""
                + ",\"strikeStatus\":\"" + (strike == null ? "NONE" : strike.status()) + "\""
                + ",\"strikeCause\":\"" + FrontierV3DiagnosticJson.quote(strike == null ? "" : strike.causeSubjectId().value())
                + "\",\"strikeAttacker\":\"" + FrontierV3DiagnosticJson.quote(strike == null ? "" : strike.subjectIds().getFirst().value())
                + "\",\"strikeTarget\":\"" + FrontierV3DiagnosticJson.quote(strike == null ? "" : strike.subjectIds().getLast().value())
                + "\",\"strikeIntent\":\"" + FrontierV3DiagnosticJson.quote(strike == null ? "" : strike.id().value())
                + "\",\"strikeReceipt\":\"" + FrontierV3DiagnosticJson.quote(strike == null ? "" : strike.postconditionObservationId().map(value -> value.value()).orElse("")) + "\""
                + ",\"strikeEpoch\":" + strikeEpoch + ",\"nextStrikeEpoch\":" + (assaultState == null ? -1 : assaultState.nextStrikeEpoch())
                + ",\"assaultStatus\":\"" + (assaultState == null ? "" : assaultState.status()) + "\""
                + ",\"strikeReceiptExact\":" + exactReceipt + ",\"strikeHealthChanged\":" + healthChanged
                + ",\"strikeHealthBefore\":" + (receipt == null ? -1 : receipt.targetHealthBefore().raw())
                + ",\"strikeHealthAfter\":" + (receipt == null ? -1 : receipt.targetHealthAfter().raw())
                + ",\"coldContinuationAvailable\":" + (assaultState != null && assaultState.status() == SettlementAssaultStatus.COLD_COMBAT
                        && state.coldSettlementAssaultSceneCandidates().stream().anyMatch(value -> value.assaultId().equals(assaultState.id())))
                + recovery + readiness.map(FrontierV3SceneDiagnosticJson::sceneReadiness).orElse("") + "}";
    }

    private static String productionTraversal(ProductionJob job) {
        if (job == null) return ",\"productionStage\":\"\",\"productionCursor\":-1,\"productionCurrent\":null,\"productionNext\":null,\"productionNextBody\":null,\"productionFutureBody\":null";
        var corridor = job.workTraversal().linearCorridorSurfaces();
        String next = job.traversalCursor() + 1 >= corridor.size() ? "null" : FrontierV3DiagnosticJson.position(corridor.get(job.traversalCursor() + 1).support());
        String nextBody = job.traversalCursor() + 1 >= corridor.size() ? "null" : FrontierV3DiagnosticJson.position(corridor.get(job.traversalCursor() + 1).standingBody());
        String futureBody = job.traversalCursor() + 2 >= corridor.size() ? "null" : FrontierV3DiagnosticJson.position(corridor.get(job.traversalCursor() + 2).standingBody());
        return ",\"productionStage\":\"" + job.workProgress().stage() + "\",\"productionCursor\":" + job.traversalCursor()
                + ",\"productionCurrent\":" + FrontierV3DiagnosticJson.position(corridor.get(job.traversalCursor()).support()) + ",\"productionNext\":" + next
                + ",\"productionNextBody\":" + nextBody + ",\"productionFutureBody\":" + futureBody;
    }

    private static String serviceTraversal(io.farfrontier.palemirror.frontier.v3.model.SettlementServiceWork work) {
        if (work == null) return ",\"serviceWork\":\"\",\"servicePhase\":\"\",\"serviceCurrent\":null,\"serviceNext\":null,\"serviceInputCursor\":-1,\"serviceInputNodes\":0,\"serviceWorkCursor\":-1,\"serviceWorkNodes\":0,\"serviceTarget\":\"\"";
        var current = FrontierSettlementServiceWorkSceneSupport.currentSurface(work);
        boolean input = work.phase() == io.farfrontier.palemirror.frontier.v3.model.SettlementServiceWorkPhase.PREPARED
                || work.phase() == io.farfrontier.palemirror.frontier.v3.model.SettlementServiceWorkPhase.APPROACH_INPUT
                || work.phase() == io.farfrontier.palemirror.frontier.v3.model.SettlementServiceWorkPhase.INPUT_ISSUE_PENDING;
        var corridor = input ? work.inputTraversal().linearCorridorSurfaces() : work.workTraversal().linearCorridorSurfaces();
        int cursor = input ? work.inputTraversalCursor() : work.workTraversalCursor();
        String next = cursor + 1 >= corridor.size() ? "null" : FrontierV3DiagnosticJson.position(corridor.get(cursor + 1).support());
        return ",\"serviceWork\":\"" + FrontierV3DiagnosticJson.quote(work.id().value()) + "\",\"servicePhase\":\"" + work.phase()
                + "\",\"serviceCurrent\":" + FrontierV3DiagnosticJson.position(current.support()) + ",\"serviceNext\":" + next
                + ",\"serviceInputCursor\":" + work.inputTraversalCursor() + ",\"serviceInputNodes\":" + work.inputTraversal().linearCorridorSurfaces().size()
                + ",\"serviceWorkCursor\":" + work.workTraversalCursor() + ",\"serviceWorkNodes\":" + work.workTraversal().linearCorridorSurfaces().size()
                + ",\"serviceTarget\":\"" + FrontierV3DiagnosticJson.quote(work.target().toString()) + "\"";
    }

    private static String patrolTraversal(io.farfrontier.palemirror.frontier.v3.model.RoutePatrol patrol) {
        if (patrol == null) return ",\"patrolStatus\":\"\",\"patrolBlockReason\":\"\",\"patrolRouteIndex\":-1,\"patrolCurrent\":null,\"patrolNextSurface\":null,\"patrolNextBody\":null";
        var bodies = FrontierRoutePatrolSceneSupport.bodies(patrol);
        var leader = bodies.get(patrol.guardId());
        io.farfrontier.palemirror.frontier.v3.model.BodyPosition nextBody = null;
        if (patrol.active()) {
            try { nextBody = FrontierRoutePatrolSceneSupport.bodies(patrol.advanceFormation()).get(patrol.guardId()); }
            catch (IllegalArgumentException unavailable) { nextBody = null; }
        }
        return ",\"patrolStatus\":\"" + patrol.status() + "\",\"patrolBlockReason\":\""
                + patrol.blockReason().map(Enum::name).orElse("") + "\",\"patrolRouteIndex\":" + patrol.routeIndex()
                + ",\"patrolCurrent\":" + FrontierV3DiagnosticJson.position(leader.supportingSurface().support())
                + ",\"patrolNextSurface\":" + (nextBody == null ? "null" : FrontierV3DiagnosticJson.position(nextBody.supportingSurface().support()))
                + ",\"patrolNextBody\":" + (nextBody == null ? "null" : FrontierV3DiagnosticJson.position(nextBody));
    }

    private static SceneLease currentLease(FrontierWorldState state, SubjectId sceneSubject) {
        return state.sceneLeases().values().stream().filter(lease -> FrontierSceneBehaviors.owns(lease, sceneSubject))
                .max(java.util.Comparator.comparingInt((SceneLease lease) -> lease.status() == SceneLeaseStatus.CLOSED ? 0 : 1)
                        .thenComparing(SceneLease::handoffInstant).thenComparingLong(SceneLease::revision).thenComparing(SceneLease::id)).orElse(null);
    }

    private static PhysicalIntent currentAssaultStrike(FrontierWorldState state, SettlementAssault assault) {
        if (assault == null) return null;
        return state.physicalIntents().values().stream().filter(value -> value.kind() == io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind.SCENE_STRIKE)
                .filter(value -> SettlementAssaultCauseIdentity.belongsTo(assault.id(), value.causeSubjectId()))
                .max(java.util.Comparator.comparingLong((PhysicalIntent value) -> SettlementAssaultCauseIdentity.epoch(assault.id(), value.causeSubjectId()))
                        .thenComparing(PhysicalIntent::id)).orElse(null);
    }

    private static String sceneReadiness(FrontierV3SceneReadiness.Value value) {
        return ",\"physicalReadiness\":{\"bodies\":\"" + FrontierV3DiagnosticJson.quote(value.bodies()) + "\",\"carrier\":\"" + FrontierV3DiagnosticJson.quote(value.carrier())
                + "\",\"serviceDemand\":\"" + FrontierV3DiagnosticJson.quote(value.serviceDemand()) + "\",\"serviceMotion\":\"" + FrontierV3DiagnosticJson.quote(value.serviceMotion())
                + "\",\"serviceInput\":\"" + FrontierV3DiagnosticJson.quote(value.serviceInput()) + "\",\"members\":" + FrontierV3DiagnosticJson.strings(value.members()) + "}";
    }
}

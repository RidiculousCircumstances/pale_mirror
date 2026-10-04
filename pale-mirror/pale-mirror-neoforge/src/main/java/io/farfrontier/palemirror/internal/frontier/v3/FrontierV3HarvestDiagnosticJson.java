package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.CheckpointImage;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.ActorLocation;
import io.farfrontier.palemirror.frontier.v3.model.ResourceSiteHarvestCargo;
import io.farfrontier.palemirror.frontier.v3.model.Bioform;
import io.farfrontier.palemirror.frontier.v3.model.BlockPosition;
import io.farfrontier.palemirror.frontier.v3.model.BodyPosition;
import io.farfrontier.palemirror.frontier.v3.model.ContainerRecord;
import io.farfrontier.palemirror.frontier.v3.model.ContainerSurface;
import io.farfrontier.palemirror.frontier.v3.model.ExactItemStack;
import io.farfrontier.palemirror.frontier.v3.model.FencedRecoveryBinding;
import io.farfrontier.palemirror.frontier.v3.model.FencedRecoveryTombstone;
import io.farfrontier.palemirror.frontier.v3.model.FrontierResourceSitePlan;
import io.farfrontier.palemirror.frontier.v3.model.FrontierReadabilityPlan;
import io.farfrontier.palemirror.frontier.v3.model.FrontierSceneLabels;
import io.farfrontier.palemirror.frontier.v3.model.FrontierResourceSiteHarvestSceneSupport;
import io.farfrontier.palemirror.frontier.v3.model.HumanAssignmentProjection;
import io.farfrontier.palemirror.frontier.v3.model.HumanAssignment;
import io.farfrontier.palemirror.frontier.v3.model.FrontierSettlementWorkDiagnostic;
import io.farfrontier.palemirror.frontier.v3.model.FrontierMarketOrderDiagnostic;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.DiagnosticIncident;
import io.farfrontier.palemirror.frontier.v3.model.DiagnosticSubject;
import io.farfrontier.palemirror.frontier.v3.process.ResourceSiteHarvestProcess;
import io.farfrontier.palemirror.frontier.v3.model.DiagnosticSubjectKind;
import io.farfrontier.palemirror.frontier.v3.process.HivePerceptionProcess;
import io.farfrontier.palemirror.frontier.v3.process.SettlementProvisionProcess;
import io.farfrontier.palemirror.frontier.v3.model.HiveNutrientReceipt;
import io.farfrontier.palemirror.frontier.v3.model.HiveNutrientTransfer;
import io.farfrontier.palemirror.frontier.v3.model.InventoryCustody;
import io.farfrontier.palemirror.frontier.v3.model.HiveGrowthJob;
import io.farfrontier.palemirror.frontier.v3.model.MarketWorkOrder;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalDelta;
import io.farfrontier.palemirror.frontier.v3.model.ProductionJob;
import io.farfrontier.palemirror.frontier.v3.model.ResidentProfile;
import io.farfrontier.palemirror.frontier.v3.model.ResidentBirthJob;
import io.farfrontier.palemirror.frontier.v3.model.ResidentNutritionStatus;
import io.farfrontier.palemirror.frontier.v3.model.CustodyAccount;
import io.farfrontier.palemirror.frontier.v3.model.ClaimAllocation;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalStackAddress;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalStackBinding;
import io.farfrontier.palemirror.frontier.v3.model.ResourceCustody;
import io.farfrontier.palemirror.frontier.v3.model.ResourceLot;
import io.farfrontier.palemirror.frontier.v3.model.ResourceSite;
import io.farfrontier.palemirror.frontier.v3.model.ResourceSiteHarvestJob;
import io.farfrontier.palemirror.frontier.v3.model.ResourceSiteHarvestGoal;
import io.farfrontier.palemirror.frontier.v3.model.ResourceSiteHarvestLineage;
import io.farfrontier.palemirror.frontier.v3.model.ResourceSiteHarvestProgress;
import io.farfrontier.palemirror.frontier.v3.model.ResourceSiteLifecycle;
import io.farfrontier.palemirror.frontier.v3.model.SceneLease;
import io.farfrontier.palemirror.frontier.v3.model.RouteConstruction;
import io.farfrontier.palemirror.frontier.v3.model.RouteMaintenance;
import io.farfrontier.palemirror.frontier.v3.model.RouteOperation;
import io.farfrontier.palemirror.frontier.v3.model.SettlementProvision;
import io.farfrontier.palemirror.frontier.v3.model.StrategicTask;
import io.farfrontier.palemirror.frontier.v3.model.StrategicTaskKind;
import io.farfrontier.palemirror.frontier.v3.model.MedicalEvacuationOperation;
import io.farfrontier.palemirror.frontier.v3.process.EngineeringEquipmentProcess;
import io.farfrontier.palemirror.frontier.v3.process.FrontierWorldProcessCatalog;
import io.farfrontier.palemirror.frontier.v3.process.PhysicalIntentLifecycleCompositionDiagnostic;

import java.util.Objects;
import java.util.Optional;
import static io.farfrontier.palemirror.internal.frontier.v3.FrontierV3DiagnosticJson.*;

/** Read-only field work and site diagnostics from one canonical checkpoint. */
final class FrontierV3HarvestDiagnosticJson {
    private FrontierV3HarvestDiagnosticJson() { }

    static String harvestProcess(CheckpointImage checkpoint, FrontierWorldState state, ResourceSiteHarvestJob job) {
        ResourceSiteLifecycle lifecycle = state.resourceSites().site(job.siteId());
        PhysicalIntent intent = state.physicalIntents().get(job.intentId());
        ActorLocation actor = state.actorLocations().get(job.workerId());
        // A released harvest remains in the durable registry as a receipt.  It must not hide a
        // later PREPARED/HOT retry for the same job merely because its historical lease id sorts
        // first: F0.V's process view reports the current ownership fact, not an archive index.
        SceneLease lease = FrontierV3DiagnosticExecutorJson.currentLease(state, job.id());
        var schedules = checkpoint.schedules().stream()
                .filter(value -> ResourceSiteHarvestProcess.coldProgress(job, value.dueAt().ticks()).equals(value))
                .sorted().limit(4).toList();
        String scheduleEntries = schedules.stream().map(value -> "{\"id\":\"" + quote(value.id().value())
                + "\",\"dueAt\":" + value.dueAt().ticks() + ",\"kind\":\"" + quote(value.kind())
                + "\",\"weight\":" + value.weight() + "}").reduce((left, right) -> left + "," + right)
                .map(value -> "[" + value + "]").orElse("[]");
        // A CLOSED receipt remains available through the scene diagnostic, but it is no longer
        // a current process claim.  Exposing it here would turn an already released observer
        // hand-off into a false HOT/COLD semantic difference.
        String leaseValue = lease == null || lease.status() == io.farfrontier.palemirror.frontier.v3.model.SceneLeaseStatus.CLOSED ? "null" : "{\"id\":\"" + quote(lease.id().value())
                + "\",\"status\":\"" + lease.status() + "\",\"revision\":" + lease.revision()
                + ",\"members\":" + lease.members().size() + ",\"body\":"
                + (lease.memberBody(state.actorLocations(), job.workerId()) == null ? "null" : position(lease.memberBody(state.actorLocations(), job.workerId()))) + "}";
        // A normal client may complete its short observed HOT turn before the following pilot
        // diagnostic.  Preserve the same closed lease as historical evidence without reviving
        // it as a current authority claim; this distinguishes an exact crop-1 ingress from a
        // newly invented crop-0 retry even after the physical scene has released.
        String lastLeaseValue = lease == null ? "null" : "{\"id\":\"" + quote(lease.id().value())
                + "\",\"status\":\"" + lease.status() + "\",\"revision\":" + lease.revision()
                + ",\"members\":" + lease.members().size() + "}";
        String intentStatus = intent == null ? "MISSING" : intent.status().name();
        String dutyPhase = job.navigationBlock().isPresent() ? "ROUTE_BLOCKED:" + intentStatus
                : (ResourceSiteHarvestGoal.actorAtWorkCell(state, job) ? "HARVESTING:" : "TRAVELLING:") + intentStatus;
        String intentKind = intent == null ? "MISSING" : intent.kind().name();
        String intentObservationId = intent == null || intent.postconditionObservationId().isEmpty() ? "null"
                : "\"" + quote(intent.postconditionObservationId().orElseThrow().value()) + "\"";
        String obstruction = lifecycle.conflictDisposition().map(FrontierV3HarvestDiagnosticJson::conflict).orElse("null");
        String actorBody = actor == null ? "null" : position(actor.body());
        var cycle = state.resourceSites().cycle(job.siteId());
        ResourceSiteHarvestGoal semanticGoal = ResourceSiteHarvestGoal.current(state, job);
        String goalStations = semanticGoal.legalStations().stream()
                .map(station -> position(station.support()))
                .collect(java.util.stream.Collectors.joining(",", "[", "]"));
        return base("process", job.id().value(), checkpoint) + ",\"status\":\"ok\",\"family\":\"frontier.resource-site-harvest\""
                + ",\"identity\":{\"job\":\"" + quote(job.id().value()) + "\",\"worker\":\"" + quote(job.workerId().value())
                + "\",\"workerPresentation\":\"" + quote(FrontierSceneLabels.actor(state, job.workerId(), false))
                + "\",\"outputItem\":\"" + quote(job.outputItemId().value()) + "\"}"
                + ",\"claims\":{\"task\":\"" + quote(job.taskId().value()) + "\",\"site\":\"" + quote(job.siteId().value())
                + "\",\"worker\":\"" + quote(job.workerId().value()) + "\",\"intent\":\"" + quote(job.intentId().value())
                + "\",\"outputSlot\":" + job.outputSlot().slot() + ",\"lease\":" + leaseValue
                + ",\"lastLease\":" + lastLeaseValue + "}"
                + ",\"conservation\":{\"outputItem\":\"" + quote(job.outputItemId().value()) + "\",\"completedCropSlots\":"
                + job.progress().completedCropSlots() + ",\"pendingCropSlot\":" + job.progress().pendingCropSlotIndex()
                + ",\"nextCropSlot\":" + (job.progress().complete() ? -1 : job.progress().nextCropSlotIndex())
                + ",\"deferredMaterializationSlots\":" + job.progress().completedCropSlots()
                + ",\"totalCropSlots\":" + job.progress().totalCropSlots()
                + ",\"harvestedYield\":" + job.harvestedYieldQuantity()
                + ",\"fieldHarvestedYield\":" + cycle.harvestedCount()
                + ",\"deliveredYield\":" + job.deliveredYieldQuantity()
                + ",\"carriedYield\":" + ResourceSiteHarvestCargo.quantity(state, job)
                + ",\"returningForBatch\":" + job.returningForBatch() + "}"
                + ",\"labour\":" + job.progress().work().map(work -> "{\"requiredMilliWork\":" + work.requiredMilliWork()
                        + ",\"completedMilliWork\":" + work.completedMilliWork()
                        + ",\"projectedMilliWork\":" + work.completedAt(Math.max(checkpoint.instant().ticks(), work.evaluatedAtTick()))
                        + ",\"ratePermille\":" + work.ratePermille() + ",\"evaluatedAtTick\":" + work.evaluatedAtTick()
                        + ",\"activeUntilTick\":" + work.activeUntilTick() + "}").orElse("null")
                + ",\"schedule\":{\"count\":" + checkpoint.schedules().stream()
                        .filter(value -> ResourceSiteHarvestProcess.coldProgress(job, value.dueAt().ticks()).equals(value)).count()
                + ",\"entries\":" + scheduleEntries + "}"
                + ",\"movement\":{\"goalBlock\":" + job.navigationBlock().map(block -> "{\"target\":" + position(block.target().support())
                        + ",\"layoutRevision\":" + block.layoutRevision()
                        + ",\"reason\":\"" + block.reason() + "\"}").orElse("null")
                + ",\"actorBody\":" + actorBody + "}"
                + ",\"semanticGoal\":{\"kind\":\"" + semanticGoal.kind() + "\",\"worker\":\""
                + quote(semanticGoal.workerId().value()) + "\",\"arrivalContract\":\"" + semanticGoal.arrivalContract()
                + "\",\"layoutRevision\":"
                + semanticGoal.layoutRevision() + ",\"nextWorkSlot\":" + semanticGoal.nextWorkSlot()
                + ",\"cellId\":" + semanticGoal.cellId().map(id -> Long.toString(id.value())).orElse("null")
                + ",\"capability\":\"" + semanticGoal.capability() + "\",\"legalStations\":" + goalStations + "}"
                + ",\"result\":{\"sitePhase\":\"" + lifecycle.phase() + "\",\"intentKind\":\"" + intentKind
                + "\",\"intentStatus\":\"" + intentStatus + "\",\"dutyPhase\":\"" + dutyPhase + "\",\"intentObservationId\":" + intentObservationId
                + ",\"obstruction\":" + obstruction + ",\"complete\":" + job.progress().complete() + "}}";
    }
    static String site(String id, CheckpointImage checkpoint, FrontierWorldState state) {
        SubjectId subject = subject(id).orElse(null);
        ResourceSiteLifecycle lifecycle = subject == null ? null : state.resourceSites().sites().get(subject);
        ResourceSite site = subject == null ? null : state.resourceSiteDescriptors().get(subject);
        if (lifecycle == null || site == null) return unavailable("site", id, checkpoint, "not_found");
        String work = java.util.stream.Stream.concat(lifecycle.preparationWork().stream().map(value -> value.id().value()),
                lifecycle.harvestJobs().keySet().stream().sorted().map(SubjectId::value))
                .map(value -> "\"" + quote(value) + "\"").collect(java.util.stream.Collectors.joining(",", "[", "]"));
        // A generic CONFLICT phase cannot tell an operator whether restart reconciliation,
        // a player action, or a foreign/damaged facility caused the isolation.  Surface the
        // durable typed disposition at the site boundary as well as on a process receipt.
        String conflict = lifecycle.conflictDisposition().map(FrontierV3HarvestDiagnosticJson::conflict).orElse("null");
        String terminal = lifecycle.harvestLineages().values().stream().sorted(java.util.Comparator.comparing(value -> value.predecessorIntentId().value())).map(lineage -> {
            var output = state.inventory().items().get(lineage.outputItemId());
            var intent = state.physicalIntents().get(lineage.predecessorIntentId());
            boolean owned = output != null && output.id().equals(lineage.outputItemId()) && output.custody().equals(lineage.outputSlot());
            String successor = harvestSuccessor(state, lineage);
            boolean physicalReceiptConfirmed = intent != null && intent.status() == io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus.CONFIRMED;
            return "{\"job\":\"" + quote(lineage.predecessorJobId().value()) + "\",\"worker\":\"" + quote(lineage.workerId().value())
                    + "\",\"intent\":\"" + quote(lineage.predecessorIntentId().value())
                    + "\",\"outputItem\":\"" + quote(lineage.outputItemId().value()) + "\",\"outputSlot\":" + lineage.outputSlot().slot()
                    // `outputOwned` deliberately means the original exact wheat still occupies its
                    // depot slot.  A completed COLD conversion removes that stack once; the retained
                    // successor receipt below, rather than a missing predecessor stack, is the
                    // durable zero-sum ownership fact.
                    + ",\"outputOwned\":" + owned + ",\"canonicalSuccessor\":" + lineage.composedIntoCanonicalSuccessor(state)
                    + ",\"successor\":" + successor + ",\"physicalReceiptResolved\":" + lineage.outputReceiptResolved()
                    + ",\"physicalReceiptConfirmed\":" + physicalReceiptConfirmed
                    + ",\"intentStatus\":\"" + (intent == null ? "MISSING" : intent.status().name()) + "\",\"terminalBody\":" + position(lineage.terminalBody())
                    + ",\"causalTrace\":" + harvestCausality(lineage.causality()) + "}";
        }).collect(java.util.stream.Collectors.joining(",", "[", "]"));
        BlockPosition boardPosition = FrontierReadabilityPlan.compile(state).boards().get(subject).position();
        return base("site", id, checkpoint) + ",\"status\":\"ok\",\"owner\":\"" + quote(site.settlementId().value())
                + "\",\"facility\":\"" + quote(site.facilityId().value()) + "\",\"phase\":\"" + lifecycle.phase()
                + "\",\"growthEpoch\":" + lifecycle.growthEpoch() + ",\"growthStage\":" + lifecycle.growthStage()
                + ",\"activeWork\":" + work + ",\"conflictDisposition\":" + conflict + ",\"terminalHarvest\":" + terminal + ",\"firstCrop\":" + position(site.cropSlots().getFirst())
                + ",\"lastCrop\":" + position(site.cropSlots().getLast()) + ",\"boardPosition\":" + position(boardPosition) + "}";
    }

    /** Bounded retained COLD/HOT receipt lineage; this query never derives missing stages. */
    private static String harvestCausality(io.farfrontier.palemirror.frontier.v3.model.ResourceSiteHarvestCausality value) {
        return "{\"coldSchedule\":\"" + quote(value.coldScheduleId()) + "\",\"coldDueAt\":" + value.coldDueAt()
                + ",\"hotLeases\":" + value.hotLeaseIds().stream().map(id -> "\"" + quote(id.value()) + "\"")
                .collect(java.util.stream.Collectors.joining(",", "[", "]"))
                + ",\"intent\":\"" + quote(value.intentId().value()) + "\",\"expectedPhysical\":\"" + quote(value.expectedPhysical())
                + "\",\"observedPhysical\":\"" + quote(value.observedPhysical()) + "\",\"reconciliation\":\""
                + quote(value.reconciliation()) + "\",\"completeForColdHotReceipt\":" + value.completeForColdHotReceipt() + "}";
    }

    /** Read-only active-or-terminal production link for the exact completed harvest input. */
    private static String harvestSuccessor(FrontierWorldState state, ResourceSiteHarvestLineage lineage) {
        return java.util.stream.Stream.concat(
                state.productionJobs().values().stream()
                        .filter(job -> lineage.outputItemId().equals(job.consumedItemId())
                                && job.inputHold() instanceof io.farfrontier.palemirror.frontier.v3.model.ProductionInputHold.Cold)
                        .map(job -> "{\"state\":\"ACTIVE_COLD\",\"job\":\"" + quote(job.id().value())
                                + "\",\"worker\":\"" + quote(job.workerId().value()) + "\",\"inputItem\":\""
                                + quote(job.consumedItemId().value()) + "\",\"outputItem\":\"" + quote(job.outputItemId().value())
                                + "\",\"outputKind\":\"" + quote(job.outputItemKind()) + "\",\"outputCount\":" + job.outputCount() + "}"),
                state.companies().market().workOrders().values().stream().flatMap(order -> order.terminalReceipt().stream()
                        .filter(receipt -> receipt.inputRepresentation() == io.farfrontier.palemirror.frontier.v3.model.TerminalProductionReceipt.ResourceRepresentation.EXACT_ITEM
                                && receipt.outputRepresentation() == io.farfrontier.palemirror.frontier.v3.model.TerminalProductionReceipt.ResourceRepresentation.EXACT_ITEM
                                && lineage.outputItemId().equals(receipt.inputId()))
                        .map(receipt -> "{\"state\":\"TERMINAL\",\"order\":\"" + quote(order.id().value()) + "\",\"job\":\"" + quote(receipt.jobId().value())
                                + "\",\"worker\":\"" + quote(receipt.workerId().value()) + "\",\"inputItem\":\"" + quote(receipt.inputId().value())
                                + "\",\"outputItem\":\"" + quote(receipt.outputId().value()) + "\",\"outputKind\":\"" + quote(receipt.outputKind())
                                + "\",\"outputCount\":" + receipt.outputCount() + "}")))
                .findFirst().orElse("null");
    }

    private static String conflict(io.farfrontier.palemirror.frontier.v3.model.ResourceSiteConflictDisposition value) {
        var incident = value.incident();
        return "{\"position\":" + position(value.position()) + ",\"reason\":\"" + value.reason() + "\",\"policy\":\"" + value.policy()
                + "\",\"incident\":{\"id\":\"" + quote(incident.id()) + "\",\"category\":\"" + incident.category()
                + "\",\"categoryTag\":" + incident.diagnostic().category().wireTag() + ",\"reason\":\"" + quote(incident.reason())
                + "\",\"reasonTag\":" + incident.diagnostic().reason().wireTag() + ",\"ownerKind\":\"" + incident.diagnostic().owner().kind()
                + "\",\"owner\":\"" + quote(incident.ownerId().value()) + "\",\"subjectKind\":\"" + incident.diagnostic().subject().kind()
                + "\",\"subject\":\"" + quote(incident.subjectId().value()) + "\",\"source\":\"" + quote(incident.source())
                + "\",\"expected\":\"" + quote(incident.expectedFact()) + "\",\"observed\":\"" + quote(incident.observedFact())
                + "\",\"preCanonical\":\"" + quote(incident.preCanonicalFact()) + "\",\"postCanonical\":\"" + quote(incident.postCanonicalFact())
                + "\",\"disposition\":\"" + quote(incident.disposition()) + "\",\"dispositionTag\":" + incident.diagnostic().disposition().wireTag()
                + ",\"traceCorrelation\":\"" + quote(incident.traceCorrelation()) + "\"}}";
    }
}

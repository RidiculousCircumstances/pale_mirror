package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Fresh-world bread admission: one baker, one declared facility and one station identity. */
final class BakeryJobAdmission {
    private BakeryJobAdmission() { }

    static ProductionJob exact(FrontierWorldState state, StrategicTask task, Settlement settlement,
                               SettlementStructure workshop, ExactItemStack input) {
        if (input.count() != 64 || !input.economicOwnerId().equals(settlement.id())
                || !"minecraft:wheat".equals(input.itemKind()))
            throw new IllegalArgumentException("bakery exact input must be 64 settlement-owned wheat");
        ResidentProfile worker = baker(state, settlement);
        SubjectId jobId = ProductionProcess.jobId(task);
        String stem = jobId.value().substring("job:".length());
        return new ProductionJob(jobId, task.id(), settlement.id(), workshop.id(), worker.id(), input.id(),
                new ProductionInputHold.Materialized(input.id()),
                new SubjectId("item:" + stem + "-bread"), "minecraft:bread", input.count(),
                ProductionWorkProgress.notStarted(), anchor(jobId, workshop, state.actorLocations().get(worker.id())), 0,
                Optional.of(work(state, workshop, jobId,
                        new SubjectId("custody:bakery-source-" + task.id().value().substring("task:".length())))));
    }

    static ProductionJob fungible(FrontierWorldState state, StrategicTask task, Settlement settlement,
                                  SettlementStructure workshop, FungibleResourceCustodySupport.LotSelection input) {
        ResidentProfile worker = baker(state, settlement);
        SubjectId jobId = ProductionProcess.jobId(task);
        String stem = jobId.value().substring("job:".length());
        ProductionInputHold hold = ProductionResourceCustody.holdForStart(state, input,
                new SubjectId("claim:" + stem), 64);
        return new ProductionJob(jobId, task.id(), settlement.id(), workshop.id(), worker.id(), input.firstLotId(), hold,
                new SubjectId("lot:" + stem + "-bread"), "minecraft:bread", 64,
                ProductionWorkProgress.notStarted(), anchor(jobId, workshop, state.actorLocations().get(worker.id())), 0,
                Optional.of(work(state, workshop, jobId, input.accountId())));
    }

    private static ResidentProfile baker(FrontierWorldState state, Settlement settlement) {
        return FrontierWorldStateSupport.availableWorkResident(state, settlement.id(), ResidentProfession.BAKER)
                .orElseThrow(() -> new IllegalStateException("settlement lacks baker"));
    }

    private static TraversalTopology anchor(SubjectId jobId, SettlementStructure workshop, ActorLocation actor) {
        return TraversalTopology.corridor(new TraversalTopologyId("topology:bakery-anchor-" + jobId.value().replace(':', '-')),
                0L, workshop.id(), TraversalKind.PEDESTRIAN, Set.of(TraversalCapability.PEDESTRIAN),
                List.of(actor.supportingSurface()));
    }

    private static BakeryWorkState work(FrontierWorldState state, SettlementStructure workshop, SubjectId jobId,
                                        SubjectId sourceAccount) {
        ProductionStationSpec station = state.inventory().containers().values().stream()
                .flatMap(container -> container.productionStation().stream())
                .filter(value -> value.facilityId().equals(workshop.id())).reduce((left, right) -> {
                    throw new IllegalArgumentException("bakery facility has multiple declared machines");
                }).orElseThrow(() -> new IllegalArgumentException("bakery facility has no declared machine"));
        String suffix = jobId.value().replace(':', '-');
        return new BakeryWorkState(BakeryWorkState.Phase.DEPOT_PICKUP, station.id(), sourceAccount,
                new SubjectId("custody:" + suffix + "-baker-hand"), new SubjectId("custody:" + suffix + "-station"),
                sourceAccount, 0);
    }

    static FrontierWorldState admitStarted(FrontierWorldState state, SubjectId subject, ProductionStarted started) {
        ProductionJob job = started.job(); if (!subject.equals(job.settlementId())) throw new IllegalArgumentException("production event subject does not own the work");
        Settlement settlement = FrontierWorldStateSupport.settlement(state.bootstrap(), job.settlementId());
        SettlementCommitmentComposition.ADMISSION.require(state, new SettlementCommitmentAdmission.Request(
                job.taskId(), job.settlementId(), job.facilityId(), List.of(job.workerId())));
        // Every fresh-world bread admission must enter the station-custody
        // vertical. Older route-only fixture jobs cannot reopen a second
        // successful depot-slot wheat-to-bread path through a forged event.
        if (job.bakeryWork().isEmpty())
            throw new IllegalArgumentException("bread production start requires declared bakery station work");
        SettlementStructure workshop = ProductionProcess.workshop(settlement);
        if (!workshop.id().equals(job.facilityId()) || state.structureConditions().get(workshop.id()) != StructureCondition.INTACT) throw new IllegalArgumentException("production start facility is unavailable");
        if (!baker(state, settlement).id().equals(job.workerId())) throw new IllegalArgumentException("production start worker is not the deterministic baker");
        ActorExecutionCoordinator.requireOrdinaryWorkAdmission(state, job.workerId());
        ProductionProcess.validateMarketOrder(state, ProductionProcess.activeTask(state, job), job);
        return BakeryProcess.start(state, job, started.inputItemId());
    }
}

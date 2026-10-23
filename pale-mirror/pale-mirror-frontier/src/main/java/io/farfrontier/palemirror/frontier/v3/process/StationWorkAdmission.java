package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import java.util.List;

/** Admission of a producer-declared recipe, worker permission and retained custody endpoints. */
final class StationWorkAdmission {
    private StationWorkAdmission() { }
    static FrontierWorldState admit(FrontierWorldState state, SubjectId subject, ProductionStarted started) {
        ProductionJob job = started.job(); if (!subject.equals(job.settlementId())) throw new IllegalArgumentException("production event subject does not own the work");
        job.rights().validate(state, job);
        Settlement settlement = FrontierWorldStateSupport.settlement(state.bootstrap(), job.settlementId());
        SettlementCommitmentComposition.ADMISSION.require(state, new SettlementCommitmentAdmission.Request(
                job.taskId(), job.settlementId(), job.facilityId(), List.of(job.workerId())));
        // Every fresh-world station admission must enter the station-custody
        // vertical. Older route-only fixture jobs cannot reopen a second
        // successful depot-slot wheat-to-bread path through a forged event.
        if (job.stationWork().isEmpty())
            throw new IllegalArgumentException("station production start requires declared station work");
        StationWorkAdmissionNavigation.require(state, job, started.access().orElseThrow(
                () -> new IllegalArgumentException("station production start lacks selected source/output access")));
        var work = job.stationWork().orElseThrow();
        var recipe = state.bootstrap().ruleset().production().require(work.recipeId());
        if (!ProductionOutputCapacity.canAdmitRecipe(state, job.rights(), recipe, job.inputCount(), StationWorkStorage.source(job), work.maintenanceTarget()))
            throw new IllegalArgumentException("production start has no capacity for its retained output");
        SettlementStructure workshop = settlement.structures().stream().filter(value -> value.id().equals(job.facilityId()))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("production lost declared facility"));
        if (!workshop.id().equals(job.facilityId()) || state.structureConditions().get(workshop.id()) != StructureCondition.INTACT) throw new IllegalArgumentException("production start facility is unavailable");
        ResidentProfile worker = state.humanPopulation().resident(job.workerId());
        if (worker == null || !worker.settlementId().equals(settlement.id())
                || !SettlementWorkPolicy.permissions(state, settlement.id()).permits(work.permission(), worker.id()))
            throw new IllegalArgumentException("production start worker is not an authorized resident");
        StationWorkAuthorization.require(work.permission(), recipe.capability());
        ActorExecutionCoordinator.requireOrdinaryWorkAdmission(state, job.workerId());
        ProductionProcess.validateMarketOrder(state, ProductionProcess.activeTask(state, job), job);
        return StationWorkProcess.start(state, job, started.inputItemId());
    }
}

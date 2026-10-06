package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Fresh-world bread admission: one baker, one declared facility and one station identity. */
final class BakeryJobAdmission {
    static SettlementStaffingPort.Demand staffingDemand(FrontierWorldState state, SubjectId home, SettlementLabourRules.Entry rules) {
        var settlement = FrontierWorldStateSupport.settlement(state.bootstrap(), home);
        boolean intact = state.structureConditions().get(ProductionProcess.workshop(settlement).id()) == StructureCondition.INTACT;
        boolean input = intact && BakeryBatchSelection.admissible(state, ProductionRights.publicService(home)).isPresent();
        var retained = state.productionJobs().values().stream().filter(job -> job.settlementId().equals(home))
                .map(ProductionJob::workerId).collect(java.util.stream.Collectors.toSet());
        int reserve = intact ? rules.minimumLocalStaff() : 0;
        return new SettlementStaffingPort.Demand(ResidentWorkKind.BAKING, HumanCapability.INDUSTRY,
                input ? rules.targetWorkers() : reserve, reserve, rules.priority(), retained);
    }
    private BakeryJobAdmission() { }

    /** Family-owned probe for executable public or own-account production; no speculative job is retained. */
    static boolean availableWork(FrontierWorldState state, ResidentProfile resident, long atTick) {
        var settlement = FrontierWorldStateSupport.settlement(state.bootstrap(), resident.settlementId());
        var workshop = ProductionProcess.workshop(settlement);
        if (state.structureConditions().get(workshop.id()) != StructureCondition.INTACT
                || !SettlementCommitmentComposition.ADMISSION.facilityAvailable(state, workshop.id())
                || ReferenceContainerCustody.blocksCanonicalUse(state, FrontierWorldState.depotId(settlement.id()))
                || ReferenceContainerCustody.hasLiveCustody(state, FrontierWorldState.depotId(settlement.id()))
                    && !ReferenceContainerCustody.hasOperationalCustody(state, FrontierWorldState.depotId(settlement.id()))) return false;
        for (var task : state.strategicPlans().tasks().values().stream()
                .filter(task -> task.ownerId().equals(settlement.id()) && task.kind() == StrategicTaskKind.PRODUCE_BREAD
                        && task.status() == StrategicTaskStatus.PENDING)
                .sorted(java.util.Comparator.comparing(StrategicTask::id)).toList()) {
            var objective = state.strategicPlans().objectives().get(task.objectiveId());
            if (objective == null) throw new IllegalArgumentException("bakery opportunity lost its exact strategic objective");
            var rights = objective.kind() == StrategicObjectiveKind.SETTLEMENT_COMPANY_PRODUCTION
                    ? CompanyBakeryPlanning.requestingCompany(state, settlement.id()).map(CompanyBakeryPlanning::rights)
                    : Optional.of(ProductionRights.publicService(settlement.id()));
            if (rights.isEmpty()) continue;
            var batch = BakeryBatchSelection.admissible(state, rights.orElseThrow());
            if (batch.isEmpty() || batch.orElseThrow().fungibleInput().filter(input ->
                    !ProductionResourceCustody.canStart(state, input, batch.orElseThrow().quantity())).isPresent()) continue;
            var job = batch.orElseThrow().exactInput().isPresent()
                    ? exact(state, task, settlement, workshop, batch.orElseThrow().exactInput().orElseThrow(), resident, rights.orElseThrow())
                    : fungible(state, task, settlement, workshop, batch.orElseThrow().fungibleInput().orElseThrow(), resident, rights.orElseThrow());
            if (ProductionCommercialProcess.canReserve(state, job)) return true;
        }
        return false;
    }

    static ResidentWorkProvider<ProductionJob> provider(StrategicTask task, Settlement settlement,
            SettlementStructure workshop, Optional<ExactItemStack> exactInput,
            Optional<FungibleResourceCustodySupport.LotSelection> fungibleInput) {
        return new ResidentWorkProvider<>() {
            @Override public ResidentWorkKind kind() { return ResidentWorkKind.BAKING; }
            @Override public HumanCapability capability() { return HumanCapability.INDUSTRY; }
            @Override public Optional<ResidentWorkOffer<ProductionJob>> discover(
                    FrontierWorldState state, ResidentProfile resident, long atTick) {
                if (!ProductionFacilityReservations.available(state.productionJobs(), workshop.id())) return Optional.empty();
                ProductionJob job = propose(state, task, settlement, workshop, exactInput, fungibleInput, resident);
                var claims = new java.util.ArrayList<WorkReservationClaim>();
                claims.add(new WorkReservationClaim.StationPlace(job.bakeryWork().orElseThrow().stationId()));
                switch (job.inputHold()) {
                    case ProductionInputHold.FungibleCold cold -> cold.inputLots().forEach((lot, quantity) ->
                            claims.add(new WorkReservationClaim.LotQuantity(cold.accountId(), lot, quantity)));
                    case ProductionInputHold.FungibleBound bound -> bound.inputLots().forEach((lot, quantity) ->
                            claims.add(new WorkReservationClaim.LotQuantity(bound.accountId(), lot, quantity)));
                    case ProductionInputHold.Materialized exact ->
                            claims.add(new WorkReservationClaim.ExactResource(exact.itemId(), job.outputCount()));
                    case ProductionInputHold.Cold cold ->
                            claims.add(new WorkReservationClaim.ExactResource(cold.itemId(), cold.item().count()));
                }
                return Optional.of(new ResidentWorkOffer<>(kind(), resident.id(), job, claims));
            }
        };
    }

    /** Read-only family proposal; the selected worker is never rediscovered by a constructor. */
    static ProductionJob propose(FrontierWorldState state, StrategicTask task, Settlement settlement,
                                  SettlementStructure workshop, Optional<ExactItemStack> exactInput,
                                  Optional<FungibleResourceCustodySupport.LotSelection> fungibleInput,
                                  ResidentProfile worker) {
        if (ReferenceContainerCustody.hasLiveCustody(state, FrontierWorldState.depotId(settlement.id()))
                && exactInput.isPresent())
            return exact(state, task, settlement, workshop, exactInput.orElseThrow(), worker);
        return fungibleInput.map(input -> fungible(state, task, settlement, workshop, input, worker))
                .orElseGet(() -> exact(state, task, settlement, workshop, exactInput.orElseThrow(), worker));
    }

    static ProductionJob exact(FrontierWorldState state, StrategicTask task, Settlement settlement,
                               SettlementStructure workshop, ExactItemStack input, ResidentProfile worker) {
        return exact(state, task, settlement, workshop, input, worker, ProductionRights.publicService(settlement.id()));
    }
    static ProductionJob exact(FrontierWorldState state, StrategicTask task, Settlement settlement,
                               SettlementStructure workshop, ExactItemStack input, ResidentProfile worker, ProductionRights rights) {
        if (!input.economicOwnerId().equals(rights.resourceOwner().id())
                || !"minecraft:wheat".equals(input.itemKind()))
            throw new IllegalArgumentException("bakery exact input must belong to its declared production owner");
        SubjectId jobId = ProductionProcess.jobId(task);
        String stem = jobId.value().substring("job:".length());
        return new ProductionJob(jobId, task.id(), settlement.id(), workshop.id(), worker.id(), input.id(),
                new ProductionInputHold.Materialized(input.id()),
                new SubjectId("item:" + stem + "-bread"), "minecraft:bread", input.count(),
                ProductionWorkProgress.notStarted(), anchor(jobId, workshop, state.actorLocations().get(worker.id())), 0,
                Optional.of(work(state, workshop, jobId,
                        new SubjectId("custody:bakery-source-" + task.id().value().substring("task:".length()))))).withRights(rights);
    }

    static ProductionJob fungible(FrontierWorldState state, StrategicTask task, Settlement settlement,
                                  SettlementStructure workshop, FungibleResourceCustodySupport.LotSelection input,
                                  ResidentProfile worker) {
        return fungible(state, task, settlement, workshop, input, worker, ProductionRights.publicService(settlement.id()));
    }
    static ProductionJob fungible(FrontierWorldState state, StrategicTask task, Settlement settlement,
                                  SettlementStructure workshop, FungibleResourceCustodySupport.LotSelection input,
                                  ResidentProfile worker, ProductionRights rights) {
        if (input.lotQuantities().keySet().stream().anyMatch(id -> !state.inventory().fungibleResources()
                .lots().get(id).economicOwnerId().equals(rights.resourceOwner().id())))
            throw new IllegalArgumentException("bakery lot selection has a foreign production owner");
        SubjectId jobId = ProductionProcess.jobId(task);
        String stem = jobId.value().substring("job:".length());
        ProductionInputHold hold = ProductionResourceCustody.holdForStart(state, input,
                new SubjectId("claim:" + stem), input.quantity());
        return new ProductionJob(jobId, task.id(), settlement.id(), workshop.id(), worker.id(), input.firstLotId(), hold,
                new SubjectId("lot:" + stem + "-bread"), "minecraft:bread", ProductionStationRecipe.breadOutputQuantity(input.quantity()),
                ProductionWorkProgress.notStarted(), anchor(jobId, workshop, state.actorLocations().get(worker.id())), 0,
                Optional.of(work(state, workshop, jobId, input.accountId()))).withRights(rights);
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
        job.rights().validate(state, job);
        Settlement settlement = FrontierWorldStateSupport.settlement(state.bootstrap(), job.settlementId());
        SettlementCommitmentComposition.ADMISSION.require(state, new SettlementCommitmentAdmission.Request(
                job.taskId(), job.settlementId(), job.facilityId(), List.of(job.workerId())));
        // Every fresh-world bread admission must enter the station-custody
        // vertical. Older route-only fixture jobs cannot reopen a second
        // successful depot-slot wheat-to-bread path through a forged event.
        if (job.bakeryWork().isEmpty())
            throw new IllegalArgumentException("bread production start requires declared bakery station work");
        if (!ProductionOutputCapacity.canAdmitBreadBatch(state, job.rights(), job.outputCount()))
            throw new IllegalArgumentException("production start has no capacity for its retained output");
        SettlementStructure workshop = ProductionProcess.workshop(settlement);
        if (!workshop.id().equals(job.facilityId()) || state.structureConditions().get(workshop.id()) != StructureCondition.INTACT) throw new IllegalArgumentException("production start facility is unavailable");
        ResidentProfile worker = state.humanPopulation().resident(job.workerId());
        if (worker == null || !worker.settlementId().equals(settlement.id())
                || !SettlementWorkPolicy.permissions(state, settlement.id()).permits(ResidentWorkKind.BAKING, worker.id()))
            throw new IllegalArgumentException("production start worker is not an authorized bakery resident");
        ActorExecutionCoordinator.requireOrdinaryWorkAdmission(state, job.workerId());
        ProductionProcess.validateMarketOrder(state, ProductionProcess.activeTask(state, job), job);
        return BakeryProcess.start(state, job, started.inputItemId());
    }
}

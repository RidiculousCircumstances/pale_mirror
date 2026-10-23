package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Fresh-world bread admission: one baker, one declared facility and one station identity. */
final class BakeryJobAdmission {
    private BakeryJobAdmission() { }

    /** Family-owned probe for executable public or own-account production; no speculative job is retained. */
    static boolean availableWork(FrontierWorldState state, ResidentProfile resident, long atTick) {
        try { return availableOpportunity(state, resident, atTick); }
        catch (io.farfrontier.palemirror.frontier.v3.model.navigation.KnownPedestrianNavigation.RouteUnavailable pending) { return false; }
    }
    private static boolean availableOpportunity(FrontierWorldState state, ResidentProfile resident, long atTick) {
        var settlement = FrontierWorldStateSupport.settlement(state.bootstrap(), resident.settlementId());
        var workshop = ProductionProcess.workshop(settlement);
        if (state.structureConditions().get(workshop.id()) != StructureCondition.INTACT
                || !SettlementCommitmentComposition.ADMISSION.facilityAvailable(state, workshop.id())) return false;
        for (var task : state.strategicPlans().tasks().values().stream()
                .filter(task -> task.ownerId().equals(settlement.id()) && task.kind() == StrategicTaskKind.PRODUCE_AT_STATION
                        && task.status() == StrategicTaskStatus.PENDING)
                .sorted(java.util.Comparator.comparing(StrategicTask::id)).toList()) {
            var objective = state.strategicPlans().objectives().get(task.objectiveId());
            if (objective == null) throw new IllegalArgumentException("bakery opportunity lost its exact strategic objective");
            if (objective.kind() != StrategicObjectiveKind.SETTLEMENT_PRODUCE_BREAD
                    && objective.kind() != StrategicObjectiveKind.SETTLEMENT_COMPANY_PRODUCTION) continue;
            var rights = objective.kind() == StrategicObjectiveKind.SETTLEMENT_COMPANY_PRODUCTION
                    ? CompanyBakeryPlanning.requestingCompany(state, settlement.id(), atTick).map(CompanyBakeryPlanning::rights)
                    : Optional.of(ProductionRights.publicService(settlement.id()));
            if (rights.isEmpty()) continue;
            var batch = BakeryBatchSelection.admissible(state, rights.orElseThrow(), atTick);
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
            @Override public ResidentWorkKind kind() { return ResidentWorkKind.PRODUCTION; }
            @Override public HumanCapability capability() { return HumanCapability.INDUSTRY; }
            @Override public Optional<ResidentWorkOffer<ProductionJob>> discover(
                    FrontierWorldState state, ResidentProfile resident, long atTick) {
                if (!ProductionFacilityReservations.available(state.productionJobs(), workshop.id())) return Optional.empty();
                ProductionJob job = propose(state, task, settlement, workshop, exactInput, fungibleInput, resident);
                var claims = StationWorkOrders.claims(job);
                return Optional.of(new ResidentWorkOffer<>(kind(), resident.id(), job, claims));
            }
        };
    }

    /** Read-only family proposal; the selected worker is never rediscovered by a constructor. */
    static ProductionJob propose(FrontierWorldState state, StrategicTask task, Settlement settlement,
                                  SettlementStructure workshop, Optional<ExactItemStack> exactInput,
                                  Optional<FungibleResourceCustodySupport.LotSelection> fungibleInput,
                                  ResidentProfile worker) {
        if (ReferenceContainerCustody.hasLiveCustody(state, exactInput.map(input -> ((InventoryCustody.ContainerSlot) input.custody()).containerId())
                    .orElseGet(() -> ((ResourceCustody.Container) state.inventory().fungibleResources().accounts().get(fungibleInput.orElseThrow().accountId()).custody()).containerId()))
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
                || !BakeryRecipes.bread(state).ingredient().accepts(input.itemKind()))
            throw new IllegalArgumentException("bakery exact input must belong to its declared production owner");
        SubjectId jobId = ProductionProcess.jobId(task);
        String stem = jobId.value().substring("job:".length());
        var selectedRights = ProductionOutputCapacity.selectDestination(state, rights, input.count(),
                ((InventoryCustody.ContainerSlot) input.custody()).containerId()).orElseThrow(
                () -> new IllegalArgumentException("bakery has no eligible output warehouse"));
        return new ProductionJob(jobId, task.id(), settlement.id(), workshop.id(), worker.id(), input.id(),
                new ProductionInputHold.Materialized(input.id()),
                new SubjectId("item:" + stem + "-bread"), BakeryRecipes.bread(state).product().itemKind(), BakeryRecipes.bread(state).outputForInput(input.count()),
                ProductionWorkProgress.notStarted(), anchor(jobId, workshop, state.actorLocations().get(worker.id())), 0,
                Optional.of(work(state, workshop, jobId, StationWorkProduct.Representation.EXACT_ITEM,
                        new SubjectId("custody:bakery-source-" + task.id().value().substring("task:".length())),
                        ((InventoryCustody.ContainerSlot) input.custody()).containerId(), selectedRights.destinationContainerId(), input.itemKind(), input.count()))).withRights(selectedRights);
    }

    static ProductionJob fungible(FrontierWorldState state, StrategicTask task, Settlement settlement,
                                  SettlementStructure workshop, FungibleResourceCustodySupport.LotSelection input,
                                  ResidentProfile worker) {
        return fungible(state, task, settlement, workshop, input, worker, ProductionRights.publicService(settlement.id()));
    }
    static ProductionJob fungible(FrontierWorldState state, StrategicTask task, Settlement settlement,
                                  SettlementStructure workshop, FungibleResourceCustodySupport.LotSelection input,
                                  ResidentProfile worker, ProductionRights rights) {
        return StationWorkOrders.propose(state, task, workshop, worker, ResidentWorkKind.PRODUCTION,
                BakeryRecipes.bread(state), input, Optional.empty(), rights);
    }

    private static TraversalTopology anchor(SubjectId jobId, SettlementStructure workshop, ActorLocation actor) {
        return TraversalTopology.corridor(new TraversalTopologyId("topology:bakery-anchor-" + jobId.value().replace(':', '-')),
                0L, workshop.id(), TraversalKind.PEDESTRIAN, Set.of(TraversalCapability.PEDESTRIAN),
                List.of(actor.supportingSurface()));
    }

    private static StationWorkState work(FrontierWorldState state, SettlementStructure workshop, SubjectId jobId,
                                        StationWorkProduct.Representation outputRepresentation, SubjectId sourceAccount, SubjectId sourceContainer, SubjectId destinationContainer,
                                        String inputKind, int inputQuantity) {
        ProductionStationSpec station = ProductionStationSelection.usable(state, workshop.id(), BakeryRecipes.BREAD)
                .orElseThrow(() -> new IllegalArgumentException("bakery facility has no usable declared machine"));
        String suffix = jobId.value().replace(':', '-');
        return new StationWorkState(StationWorkState.Phase.DEPOT_PICKUP, ResidentWorkKind.PRODUCTION, outputRepresentation, Optional.empty(), station.id(), BakeryRecipes.BREAD,
                inputKind, inputQuantity,
                sourceAccount, sourceContainer,
                new SubjectId("custody:" + suffix + "-baker-hand"), ReferenceContainerCustody.scopeId(station.containerId()),
                FungibleResourceCustodySupport.accountAtContainer(state, destinationContainer).map(CustodyAccount::id)
                        .orElseGet(() -> ReferenceContainerCustody.scopeId(destinationContainer)));
    }

}

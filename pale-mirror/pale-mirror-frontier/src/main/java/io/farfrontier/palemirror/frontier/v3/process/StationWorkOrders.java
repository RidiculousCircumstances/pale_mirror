package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import java.util.*;

/** Constructs an order from a caller-selected recipe and stock; it does not decide demand or staffing. */
public final class StationWorkOrders {
    public static ProductionJob propose(FrontierWorldState state, StrategicTask task, SettlementStructure facility,
            ResidentProfile worker, ResidentWorkKind permission, ProductionRecipe recipe,
            FungibleResourceCustodySupport.LotSelection input, Optional<StationWorkState.MaintenanceTarget> maintenance,
            ProductionRights rights) {
        var account = state.inventory().fungibleResources().accounts().get(input.accountId());
        var kind = state.inventory().fungibleResources().lots().get(input.firstLotId()).itemKind();
        return propose(state, task, facility, worker, permission, recipe, new ProductionMaterialSupply.Bundle(List.of(
                new ProductionMaterialSupply.Source(kind, ((ResourceCustody.Container) account.custody()).containerId(), input))), maintenance, rights);
    }
    public static ProductionJob propose(FrontierWorldState state, StrategicTask task, SettlementStructure facility,
            ResidentProfile worker, ResidentWorkKind permission, ProductionRecipe recipe, ProductionMaterialSupply.Bundle bundle,
            Optional<StationWorkState.MaintenanceTarget> maintenance, ProductionRights rights) {
        var input = bundle.sources().getFirst().selection();
        var owner = rights.resourceOwner().id();
        var ledger = state.inventory().fungibleResources();
        var kind = ledger.lots().get(input.firstLotId()).itemKind();
        if (!task.ownerId().equals(worker.settlementId()) || !recipe.acceptsInput(kind)
                || input.lotQuantities().keySet().stream().anyMatch(id -> !ledger.lots().get(id).economicOwnerId().equals(owner)
                    || !ledger.lots().get(id).itemKind().equals(kind)))
            throw new IllegalArgumentException("station proposal lost its declared material owner or recipe");
        recipe.requireTransformation(recipe.capability(), bundle.quantities(), recipe.product().itemKind(), recipe.outputForInput(bundle.quantity()));
        for (var source : bundle.sources()) {
            var account = ledger.accounts().get(source.selection().accountId());
            if (!(account.custody() instanceof ResourceCustody.Container holder) || !holder.containerId().equals(source.containerId())
                    || source.selection().lotQuantities().keySet().stream().anyMatch(id -> !ledger.lots().get(id).economicOwnerId().equals(owner)
                        || !ledger.lots().get(id).itemKind().equals(source.itemKind())))
                throw new IllegalArgumentException("production bundle has a foreign source, owner or material");
        }
        StationWorkAuthorization.require(permission, recipe.capability());
        var source = ((ResourceCustody.Container) ledger.accounts().get(input.accountId()).custody()).containerId();
        var station = ProductionStationSelection.usable(state, facility.id(), recipe.id()).orElseThrow();
        var destination = ProductionOutputCapacity.selectRecipeDestination(state, rights, recipe, bundle.quantity(), source,
                SettlementWorkshopServicePort.forWorkshop(facility).exteriorApproach(), maintenance).orElseThrow();
        var id = ProductionProcess.jobId(task); var token = id.value().replace(':', '-');
        var hold = ProductionResourceCustody.holdForStart(state, input, new SubjectId("claim:" + token), input.quantity());
        var representation = recipe.outcome() instanceof ProductionRecipeOutcome.Fungible
                ? StationWorkProduct.Representation.RESOURCE_LOT : StationWorkProduct.Representation.EXACT_ITEM;
        var output = maintenance.map(StationWorkState.MaintenanceTarget::itemId).orElseGet(() ->
                new SubjectId((representation == StationWorkProduct.Representation.EXACT_ITEM ? "item:" : "lot:") + token + "-output"));
        var work = new StationWorkState(maintenance.isPresent() ? StationWorkState.Phase.TARGET_PICKUP : StationWorkState.Phase.DEPOT_PICKUP,
                permission, representation, maintenance, station.id(), recipe.id(), kind, input.quantity(), input.accountId(), source,
                new SubjectId("custody:" + token + "-hand"), ContainerResourceAccounts.id(station.custody(ActorContainerItemOrder.StationPort.INPUT)),
                FungibleResourceCustodySupport.accountAtContainer(state, destination.destinationContainerId()).map(CustodyAccount::id)
                        .orElseGet(() -> ReferenceContainerCustody.scopeId(destination.destinationContainerId())));
        var queued = new ArrayList<StationWorkMaterials.Pickup>();
        for (int index = 1; index < bundle.sources().size(); index++) {
            var next = bundle.sources().get(index);
            queued.add(new StationWorkMaterials.Pickup(next.itemKind(), next.containerId(), next.selection().accountId(),
                    new SubjectId("claim:" + token + "-input-" + index), next.selection().lotQuantities()));
        }
        work = work.withMaterials(new StationWorkMaterials(queued, List.of(), 0));
        var anchor = TraversalTopology.corridor(new TraversalTopologyId("topology:station-anchor-" + token), 0,
                facility.id(), TraversalKind.PEDESTRIAN, Set.of(TraversalCapability.PEDESTRIAN),
                List.of(state.actorLocations().get(worker.id()).supportingSurface()));
        return new ProductionJob(id, task.id(), worker.settlementId(), facility.id(), worker.id(), input.firstLotId(), hold,
                output, recipe.product().itemKind(), recipe.outputForInput(bundle.quantity()), ProductionWorkProgress.notStarted(),
                anchor, 0, Optional.of(work)).withRights(destination);
    }
    public static List<WorkReservationClaim> claims(ProductionJob job) {
        var work = job.stationWork().orElseThrow(); var claims = new ArrayList<WorkReservationClaim>();
        claims.add(new WorkReservationClaim.StationPlace(work.stationId()));
        work.maintenanceTarget().ifPresent(target -> claims.add(new WorkReservationClaim.ExactResource(target.itemId(), 1)));
        switch (job.inputHold()) {
            case ProductionInputHold.FungibleCold hold -> hold.inputLots().forEach((lot, quantity) ->
                    claims.add(new WorkReservationClaim.LotQuantity(hold.accountId(), lot, quantity)));
            case ProductionInputHold.FungibleBound hold -> hold.inputLots().forEach((lot, quantity) ->
                    claims.add(new WorkReservationClaim.LotQuantity(hold.accountId(), lot, quantity)));
            case ProductionInputHold.Materialized hold -> claims.add(new WorkReservationClaim.ExactResource(hold.itemId(), job.inputCount()));
            case ProductionInputHold.Cold hold -> claims.add(new WorkReservationClaim.ExactResource(hold.itemId(), hold.item().count()));
        }
        for (var input : work.materials().queued()) input.lots().forEach((lot, quantity) ->
                claims.add(new WorkReservationClaim.LotQuantity(input.sourceAccount(), lot, quantity)));
        return List.copyOf(claims);
    }
    private StationWorkOrders() { }
}

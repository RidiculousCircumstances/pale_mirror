package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.Optional;

/** Station lifecycle service: no knowledge of bakery phases, resident needs or travel. */
public final class ProductionStationProcessing {
    private ProductionStationProcessing() { }
    public static boolean canStart(FrontierWorldState state, SubjectId stationId, ProductionRecipe.Id recipeId, int inputQuantity) {
        var recipe = state.bootstrap().ruleset().production().require(recipeId);
        recipe.outputForInput(inputQuantity);
        var machine = state.productionStations().require(stationId);
        var station = ProductionStationEnergy.record(state, stationId).productionStation().orElseThrow();
        return machine.operation().isEmpty() && machine.pendingFuel().isEmpty()
                && ProductionStationRecipe.portVacant(state, station, ActorContainerItemOrder.StationPort.OUTPUT)
                && machine.storedHeat() >= Math.multiplyExact(recipe.heatPerBatch(), inputQuantity / recipe.ingredient().quantity());
    }
    public static ProductionOperation require(FrontierWorldState state, SubjectId stationId,
                                               SubjectId orderId, ProductionRecipe.Id recipeId) {
        var operation = state.productionStations().require(stationId).operation()
                .orElseThrow(() -> new IllegalArgumentException("production order lost its station operation"));
        if (!operation.orderId().equals(orderId) || !operation.recipeId().equals(recipeId))
            throw new IllegalArgumentException("production order has a foreign station operation");
        return operation;
    }
    public static ProductionStations start(FrontierWorldState state, ProductionStationSpec station,
                                           ProductionOperation operation) {
        var registered = state.inventory().containers().get(station.containerId());
        if (registered == null || !registered.productionStation().equals(Optional.of(station))
                || !station.id().equals(operation.stationId()))
            throw new IllegalArgumentException("operation has no exact declared installed station");
        var recipe = state.bootstrap().ruleset().production().require(operation.recipeId());
        state.bootstrap().ruleset().production().requireEquipment(station.equipmentType()).require(recipe);
        if (recipe.capability() != station.capability() || recipe.outputForInput(operation.inputQuantity()) != operation.outputQuantity())
            throw new IllegalArgumentException("operation has no compatible declared recipe");
        var machine = state.productionStations().require(station.id());
        if (!canStart(state, station.id(), operation.recipeId(), operation.inputQuantity()))
            throw new IllegalArgumentException("operation cannot claim an occupied output port or unavailable energy");
        return state.productionStations().with(machine.start(operation));
    }
    public static ProductionStations progress(FrontierWorldState state, ProductionOperation previous, WorkProgress next) {
        return state.productionStations().with(state.productionStations().require(previous.stationId())
                .replace(previous, previous.withProgress(next)));
    }
    /** Loading hands the allocation lifecycle to the installed operation without changing any lot, address or epoch. */
    public static ExactInventory adoptInputOwnership(ExactInventory inventory, ProductionOperation operation) {
        if (!(operation.inputCustody() instanceof ProductionOperation.FungibleInput input)) return inventory;
        var ledger = inventory.fungibleResources();
        for (var id : input.claims().keySet()) {
            var prior = ledger.claims().get(id);
            if (prior == null || prior.quantity() != input.claims().get(id) || !prior.lotQuantities().equals(input.lots()))
                throw new IllegalArgumentException("station loading lost its exact allocated input");
            var next = new ClaimAllocation(id, operation.id().value(), prior.economicOwnerId(), prior.itemKind(),
                    prior.quantity(), prior.lotQuantities(), ClaimPurpose.STATION_PROCESSING);
            ledger = ledger.transferClaimOwner(prior, next);
        }
        return inventory.withFungibleResources(ledger);
    }
    public static ProductionStations transformed(FrontierWorldState state, ProductionOperation previous) {
        return state.productionStations().with(state.productionStations().require(previous.stationId())
                .replace(previous, previous.confirmTransformation()));
    }
    /** The requesting order owns collection until the confirmed pickup; players can still forfeit this promise. */
    public static ExactInventory reserveOutput(ExactInventory inventory, ProductionOperation operation, long epoch) {
        if (!(operation.initiator() instanceof ProductionOperation.OrderInitiator order)
                || !(operation.inputCustody() instanceof ProductionOperation.FungibleInput input)) return inventory;
        var ledger = inventory.fungibleResources(); var output = ledger.lots().get(operation.outputId());
        if (output == null) throw new IllegalArgumentException("station cannot reserve absent output");
        var claim = new ClaimAllocation(operation.outputClaimId(), order.orderId(), output.economicOwnerId(), output.itemKind(),
                operation.outputQuantity(), java.util.Map.of(output.id(), operation.outputQuantity()), ClaimPurpose.STATION_OUTPUT);
        return inventory.withFungibleResources(epoch == 0 ? ledger.reserve(claim, input.accountId())
                : ledger.reserveBound(claim, input.accountId(), epoch));
    }
    /** Resource handoff completed: collection reservation ends here, not after travel back to storage. */
    public static ExactInventory collectedOutput(ExactInventory inventory, ProductionOperation operation, SubjectId destinationAccount, long epoch) {
        if (!(operation.inputCustody() instanceof ProductionOperation.FungibleInput)) return inventory;
        var ledger = inventory.fungibleResources();
        if (!ledger.claims().containsKey(operation.outputClaimId())) throw new IllegalArgumentException("output pickup lost its collection reservation");
        return inventory.withFungibleResources(epoch == 0 ? ledger.releaseClaim(destinationAccount, operation.outputClaimId())
                : ledger.releaseBoundClaim(destinationAccount, operation.outputClaimId(), epoch));
    }
    public static ProductionStations unloaded(FrontierWorldState state, ProductionOperation previous) {
        return state.productionStations().with(state.productionStations().require(previous.stationId()).release(previous.id()));
    }
}

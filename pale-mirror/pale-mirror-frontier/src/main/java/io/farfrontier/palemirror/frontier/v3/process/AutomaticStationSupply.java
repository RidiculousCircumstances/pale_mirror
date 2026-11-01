package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.model.*;
import java.util.*;

/** Automatic-production demand adapter. Common logistics owns claims, movement and transfers. */
final class AutomaticStationSupply {
    static List<InternalHaulRequest> requests(FrontierWorldState state, ContainerRecord record, long tick) {
        var spec = record.productionStation().orElseThrow();
        var policy = AutomaticStationProduction.policy(state, spec).orElse(null);
        if (policy == null) return List.of();
        var requests=new ArrayList<InternalHaulRequest>();
        var recipe = state.bootstrap().ruleset().production().require(policy.recipeId());
        var facility = FrontierWorldStateSupport.settlement(state.bootstrap(), record.ownerId()).structures().stream()
                .filter(value -> value.id().equals(spec.facilityId())).findFirst().orElseThrow();
        var approach = SettlementWorkshopServicePort.forWorkshop(facility).exteriorApproach();
        var source = new ShipmentEndpoint.ProductionPort(record.ownerId(), spec,
                ActorContainerItemOrder.StationPort.OUTPUT, approach);
        var account = FungibleResourceCustodySupport.accountAtContainer(state, record.id(), ContainerSectionId.OUTPUT);
        var ledger = state.inventory().fungibleResources();
        int output = account.map(value -> ledger.unclaimedQuantity(value.id(), record.ownerId(), recipe.product().itemKind())).orElse(0);
        if (output > 0) {
            for (var destination : ResourceStorageSelection.candidates(state, record.ownerId(), recipe.product().itemKind(),
                    source.station(), Set.of(ResourceStorageRole.WAREHOUSE))) {
                InternalHaulRequest.select(state,record.ownerId(),source,destination,recipe.product().itemKind(),output).ifPresent(requests::add);
            }
        }
        long missing = ProductionStockDemand.read(state, record.ownerId(), recipe.product().itemKind(), tick).missing();
        long batches = Math.min(ProductionBatchSizing.maximumRecipeBatches(recipe), Math.ceilDiv(missing, recipe.product().quantity()));
        var inputAccount = FungibleResourceCustodySupport.accountAtContainer(state, record.id(), ContainerSectionId.INPUT);
        var supplied = new TreeMap<String, Long>();
        inputAccount.ifPresent(value -> value.lotQuantities().forEach((id, count) ->
                supplied.merge(ledger.lots().get(id).itemKind(), (long) count, Math::addExact)));
        state.shipments().shipments().values().stream().filter(shipment -> !shipment.terminal()
                && shipment.receiver().custody().equals(spec.custody(ActorContainerItemOrder.StationPort.INPUT)))
                .forEach(shipment -> supplied.merge(shipment.itemKind(), (long) shipment.quantity(), Math::addExact));
        var materials = ProductionRecipeInputs.select(recipe, kind -> -(supplied.getOrDefault(kind, 0L)
                + ResourceStorageSelection.sources(state, ledger, record.ownerId(), kind, spec.workerStation(), Set.of(ResourceStorageRole.WAREHOUSE))
                        .stream().mapToLong(ResourceStorageSelection.Source::quantity).sum()));
        var input = new ShipmentEndpoint.ProductionPort(record.ownerId(), spec, ActorContainerItemOrder.StationPort.INPUT, approach);
        for (var material : new TreeMap<>(materials).entrySet()) {
            int quantity = Math.toIntExact(Math.max(0, Math.multiplyExact(batches, material.getValue()) - supplied.getOrDefault(material.getKey(), 0L)));
            if (quantity == 0) continue;
            for (var warehouse : ResourceStorageSelection.candidates(state, record.ownerId(), material.getKey(), input.station(), Set.of(ResourceStorageRole.WAREHOUSE))) {
                InternalHaulRequest.select(state,record.ownerId(),warehouse,input,material.getKey(),quantity).ifPresent(requests::add);
            }
        }
        return List.copyOf(requests);
    }
    private AutomaticStationSupply() { }
}

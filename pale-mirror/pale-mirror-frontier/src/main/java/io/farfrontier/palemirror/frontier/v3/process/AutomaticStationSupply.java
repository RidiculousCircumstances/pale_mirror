package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.model.*;
import java.util.*;

/** Automatic-production demand adapter. Common logistics owns claims, movement and transfers. */
final class AutomaticStationSupply {
    static Optional<InternalShipmentDispatched> offer(FrontierWorldState state, ResidentProfile worker,
            ContainerRecord record, long tick) {
        var spec = record.productionStation().orElseThrow();
        var policy = AutomaticStationProduction.policy(state, spec).orElse(null);
        if (policy == null) return Optional.empty();
        var recipe = state.bootstrap().ruleset().production().require(policy.recipeId());
        var facility = FrontierWorldStateSupport.settlement(state.bootstrap(), record.ownerId()).structures().stream()
                .filter(value -> value.id().equals(spec.facilityId())).findFirst().orElseThrow();
        var approach = SettlementWorkshopServicePort.forWorkshop(facility).exteriorApproach();
        var source = new ShipmentEndpoint.ProductionPort(record.ownerId(), spec,
                ActorContainerItemOrder.StationPort.OUTPUT, approach);
        var account = FungibleResourceCustodySupport.accountAtContainer(state, record.id());
        var ledger = state.inventory().fungibleResources();
        int output = account.map(value -> ledger.unclaimedQuantity(value.id(), record.ownerId(), recipe.product().itemKind())).orElse(0);
        if (output > 0) {
            for (var destination : ResourceStorageSelection.candidates(state, record.ownerId(), recipe.product().itemKind(),
                    source.station(), Set.of(ResourceStorageRole.WAREHOUSE))) {
                var dispatched = InternalShipmentPlanning.prepare(state, worker, source, destination,
                        recipe.product().itemKind(), Math.min(64, output), tick);
                if (dispatched.isPresent()) return dispatched;
            }
        }
        long missing = ProductionStockDemand.read(state, record.ownerId(), recipe.product().itemKind(), tick).missing();
        long batches = (missing + recipe.product().quantity() - 1L) / recipe.product().quantity();
        long required = Math.min(64, Math.multiplyExact(batches, recipe.ingredient().quantity()));
        long supplied = account.map(value -> value.lotQuantities().entrySet().stream()
                .filter(entry -> recipe.ingredient().accepts(ledger.lots().get(entry.getKey()).itemKind()))
                .mapToLong(Map.Entry::getValue).sum()).orElse(0L);
        supplied += state.shipments().shipments().values().stream().filter(shipment -> !shipment.terminal()
                && shipment.receiver().containerId().equals(record.id()) && recipe.ingredient().accepts(shipment.itemKind()))
                .mapToLong(Shipment::quantity).sum();
        int quantity = (int) Math.max(0, required - supplied);
        if (quantity == 0) return Optional.empty();
        var input = new ShipmentEndpoint.ProductionPort(record.ownerId(), spec, ActorContainerItemOrder.StationPort.INPUT, approach);
        for (var kind : recipe.ingredient().acceptedItems().stream().sorted().toList()) {
            for (var warehouse : ResourceStorageSelection.candidates(state, record.ownerId(), kind, input.station(), Set.of(ResourceStorageRole.WAREHOUSE))) {
                var dispatched = InternalShipmentPlanning.prepare(state, worker, warehouse, input, kind, quantity, tick);
                if (dispatched.isPresent()) return dispatched;
            }
        }
        return Optional.empty();
    }
    private AutomaticStationSupply() { }
}

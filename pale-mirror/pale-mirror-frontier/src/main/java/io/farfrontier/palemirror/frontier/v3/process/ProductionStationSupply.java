package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.model.*;
import java.util.*;

/** Production declares fuel demand; existing same-owner logistics executes it. No new haul runtime. */
final class ProductionStationSupply {
    static Optional<InternalShipmentDispatched> offer(FrontierWorldState state, ResidentProfile worker, long tick) {
        var catalog = state.bootstrap().ruleset().production();
        for (var record : state.inventory().containers().values().stream().sorted(Comparator.comparing(ContainerRecord::id)).toList()) {
            if (!record.ownerId().equals(worker.settlementId()) || record.productionStation().isEmpty()
                    || ReferenceContainerCustody.blocksCanonicalUse(state, record.id())) continue;
            var station = record.productionStation().orElseThrow();
            long demand = catalog.recipes().stream().filter(recipe -> recipe.capability() == station.capability())
                    .mapToLong(recipe -> Math.multiplyExact(recipe.heatPerBatch(), (64 / recipe.ingredient().quantity()) * 2L)).max().orElse(0);
            if (demand == 0) continue;
            long available = ProductionStationEnergy.suppliedHeat(state, station.id());
            if (available >= demand) continue;
            var facility = FrontierWorldStateSupport.settlement(state.bootstrap(), worker.settlementId()).structures().stream()
                    .filter(value -> value.id().equals(station.facilityId())).findFirst().orElseThrow();
            var endpoint = new ShipmentEndpoint.ProductionPort(worker.settlementId(), station, ActorContainerItemOrder.StationPort.FUEL,
                    SettlementWorkshopServicePort.forWorkshop(facility).exteriorApproach());
            for (var fuel : catalog.fuels().stream().sorted(Comparator.comparing(ProductionCatalog.Fuel::itemKind)).toList()) {
                int quantity = (int) Math.min(64, 1L + (demand - available - 1L) / fuel.heatUnits());
                for (var source : ResourceStorageSelection.candidates(state, worker.settlementId(), fuel.itemKind(),
                        endpoint.exteriorApproach(), Set.of(ResourceStorageRole.WAREHOUSE))) {
                    var ledger = state.inventory().fungibleResources();
                    int availableAtSource = ledger.accounts().values().stream().filter(account -> account.custody().equals(source.custody()))
                            .mapToInt(account -> ledger.unclaimedQuantity(account.id(), worker.settlementId(), fuel.itemKind())).sum();
                    if (availableAtSource == 0) continue;
                    var dispatched = InternalShipmentPlanning.prepare(state, worker, source, endpoint, fuel.itemKind(),
                            Math.min(quantity, availableAtSource), tick);
                    if (dispatched.isPresent()) return dispatched;
                }
            }
        }
        return Optional.empty();
    }
    private ProductionStationSupply() { }
}

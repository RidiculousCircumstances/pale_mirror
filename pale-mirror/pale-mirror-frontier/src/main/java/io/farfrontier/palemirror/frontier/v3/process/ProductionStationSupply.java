package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.model.*;
import java.util.*;

/** Production declares machine-port demand; existing same-owner logistics executes it. */
final class ProductionStationSupply {
    static Optional<InternalShipmentDispatched> offer(FrontierWorldState state, ResidentProfile worker, long tick) {
        var catalog = state.bootstrap().ruleset().production();
        record Candidate(ContainerRecord record, long deadline) { }
        var candidates = state.inventory().containers().values().stream()
                .filter(record -> record.ownerId().equals(worker.settlementId()) && record.productionStation().isPresent()
                        && !ReferenceContainerCustody.blocksCanonicalUse(state, record.id())
                        && (record.productionStation().orElseThrow().fuelSlot().isPresent() || AutomaticStationProduction.policy(state, record.productionStation().orElseThrow()).isPresent()))
                .map(record -> new Candidate(record, StationFuelDemandComposition.deadline(state, record.productionStation().orElseThrow(), tick)))
                .sorted(Comparator.comparingLong(Candidate::deadline).thenComparing(candidate -> candidate.record().id())).toList();
        for (var candidate : candidates) {
            var record = candidate.record();
            var station = record.productionStation().orElseThrow();
            var supplied = AutomaticStationSupply.offer(state, worker, record, tick);
            if (supplied.isPresent()) return supplied;
            long demand = StationFuelDemandComposition.heat(state, station, tick);
            if (demand == 0) continue;
            long available = ProductionStationEnergy.unlitFuelTicks(state, station.id());
            if (available >= demand) continue;
            var facility = FrontierWorldStateSupport.settlement(state.bootstrap(), worker.settlementId()).structures().stream()
                    .filter(value -> value.id().equals(station.facilityId())).findFirst().orElseThrow();
            var endpoint = new ShipmentEndpoint.ProductionPort(worker.settlementId(), station, ActorContainerItemOrder.StationPort.FUEL,
                    SettlementWorkshopServicePort.forWorkshop(facility).exteriorApproach());
            for (var fuel : ProductionFuelSelection.ranked(state, worker.settlementId(), demand - available, tick)) {
                int quantity = (int) Math.min(64, 1L + (demand - available - 1L) / fuel.burnTicks());
                for (var source : ResourceStorageSelection.candidates(state, worker.settlementId(), fuel.itemKind(),
                        endpoint.exteriorApproach(), Set.of(ResourceStorageRole.WAREHOUSE))) {
                    var ledger = state.inventory().fungibleResources();
                    int availableAtSource = ledger.accounts().values().stream().filter(account -> account.custody().equals(source.custody()))
                            .mapToInt(account -> ledger.unclaimedQuantity(account.id(), worker.settlementId(), fuel.itemKind())).sum();
                    long allAvailable = ResourceStorageSelection.containers(state, worker.settlementId(), fuel.itemKind(), Set.of(ResourceStorageRole.WAREHOUSE)).stream()
                            .flatMap(id -> FungibleResourceCustodySupport.accountAtContainer(state, id).stream())
                            .mapToLong(account -> ledger.unclaimedQuantity(account.id(), worker.settlementId(), fuel.itemKind())).sum();
                    availableAtSource = (int) Math.min(availableAtSource, Math.max(0, allAvailable - ProductionFuelSelection.materialBuffer(state, fuel.itemKind())));
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

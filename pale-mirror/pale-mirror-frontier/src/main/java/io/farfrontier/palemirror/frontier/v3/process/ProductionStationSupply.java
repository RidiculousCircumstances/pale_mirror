package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.model.*;
import java.util.*;

/** Production declares machine-port demand; existing same-owner logistics executes it. */
final class ProductionStationSupply {
    static List<InternalHaulRequest> requests(FrontierWorldState state, io.farfrontier.palemirror.frontier.v3.api.SubjectId home, long tick) {
        var requests=new ArrayList<InternalHaulRequest>();
        record Candidate(ContainerRecord record, long deadline) { }
        var candidates = ProductionStationDirectory.owned(state.inventory(), home).stream()
                .filter(record -> record.ownerId().equals(home) && record.productionStation().isPresent()
                        && !ReferenceContainerCustody.blocksCanonicalUse(state, record.id())
                        && (record.productionStation().orElseThrow().fuelSlot().isPresent() || AutomaticStationProduction.policy(state, record.productionStation().orElseThrow()).isPresent()))
                .map(record -> new Candidate(record, StationFuelDemandComposition.deadline(state, record.productionStation().orElseThrow(), tick)))
                .sorted(Comparator.comparingLong(Candidate::deadline).thenComparing(candidate -> candidate.record().id())).toList();
        for (var candidate : candidates) {
            var record = candidate.record();
            var station = record.productionStation().orElseThrow();
            requests.addAll(AutomaticStationSupply.requests(state,record,tick));
            long demand = StationFuelDemandComposition.heat(state, station, tick);
            if (demand == 0) continue;
            long available = ProductionStationEnergy.unlitFuelTicks(state, station.id());
            if (available >= demand) continue;
            var facility = FrontierWorldStateSupport.settlement(state.bootstrap(), home).structures().stream()
                    .filter(value -> value.id().equals(station.facilityId())).findFirst().orElseThrow();
            var endpoint = new ShipmentEndpoint.ProductionPort(home, station, ActorContainerItemOrder.StationPort.FUEL,
                    SettlementWorkshopServicePort.forWorkshop(facility).exteriorApproach());
            for (var fuel : ProductionFuelSelection.ranked(state, home, demand - available, tick)) {
                int quantity = (int) Math.min(64, 1L + (demand - available - 1L) / fuel.burnTicks());
                for (var source : ResourceStorageSelection.candidates(state, home, fuel.itemKind(),
                        endpoint.exteriorApproach(), Set.of(ResourceStorageRole.WAREHOUSE))) {
                    var ledger = state.inventory().fungibleResources();
                    int availableAtSource = FungibleResourceCustodySupport.accountsAt(ledger, source.custody()).stream()
                            .mapToInt(account -> ledger.unclaimedQuantity(account.id(), home, fuel.itemKind())).sum();
                    long allAvailable = ResourceStorageSelection.containers(state, home, fuel.itemKind(), Set.of(ResourceStorageRole.WAREHOUSE)).stream()
                            .flatMap(id -> FungibleResourceCustodySupport.accountAtContainer(state, id).stream())
                            .mapToLong(account -> ledger.unclaimedQuantity(account.id(), home, fuel.itemKind())).sum();
                    availableAtSource = (int) Math.min(availableAtSource, Math.max(0, allAvailable - ProductionFuelSelection.materialBuffer(state, fuel.itemKind())));
                    if (availableAtSource == 0) continue;
                    InternalHaulRequest.select(state,home,source,endpoint,fuel.itemKind(),Math.min(quantity,availableAtSource)).ifPresent(requests::add);
                }
            }
        }
        return List.copyOf(requests);
    }
    private ProductionStationSupply() { }
}

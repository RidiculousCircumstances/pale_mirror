package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.*;

/** Machine energy owner. Catalog supplies properties; the existing ledger consumes real stock. */
public final class ProductionStationEnergy {
    /** Supply forecast only: stored fuel and admitted deliveries do not authorize recipe execution. */
    public static long suppliedHeat(FrontierWorldState state, SubjectId stationId) {
        var record = record(state, stationId);
        // Historical job/receiver declarations outlive a destroyed machine while
        // their owners select a replacement. Retired physical capacity supplies
        // no energy; it must not require the already retired runtime instance.
        // Missing runtime on any non-retired declaration remains an invariant error.
        if (state.inventory().surfaces().get(record.id()).status() == ContainerSurfaceStatus.RETIRED) return 0;
        var ledger = state.inventory().fungibleResources();
        var account = FungibleResourceCustodySupport.accountAtContainer(state, record.id());
        long available = state.productionStations().require(stationId).storedHeat();
        for (var fuel : state.bootstrap().ruleset().production().fuels()) {
            long stored = account.map(value -> (long) ledger.unclaimedQuantity(value.id(), record.ownerId(), fuel.itemKind())).orElse(0L);
            long incoming = state.shipments().shipments().values().stream().filter(shipment -> !shipment.terminal()
                    && shipment.receiver().containerId().equals(record.id()) && shipment.itemKind().equals(fuel.itemKind()))
                    .mapToLong(Shipment::quantity).sum();
            available = Math.addExact(available, Math.multiplyExact(fuel.heatUnits(), Math.addExact(stored, incoming)));
        }
        return available;
    }
    public static Optional<ProductionStationFuelBurn> next(FrontierWorldState state, SubjectId stationId) {
        var machine = state.productionStations().require(stationId);
        var record = record(state, stationId); var station = record.productionStation().orElseThrow();
        var catalog = state.bootstrap().ruleset().production();
        long target = StationFuelDemandComposition.backedHeat(state, station);
        if (machine.operation().isPresent() || machine.pendingFuel().isPresent() || machine.storedHeat() >= target
                || ReferenceContainerCustody.blocksCanonicalUse(state, record.id())
                || ContainerPhysicalAuthorityComposition.pending(state, record.id())) return Optional.empty();
        var account = FungibleResourceCustodySupport.accountAtContainer(state, record.id());
        if (account.isEmpty()) return Optional.empty();
        boolean hot = ReferenceContainerCustody.hasLiveCustody(state, record.id());
        if (hot && !ReferenceContainerCustody.hasOperationalCustody(state, record.id())) return Optional.empty();
        for (var fuel : catalog.fuels().stream().sorted(Comparator.comparing(ProductionCatalog.Fuel::itemKind)).toList()) {
            var selected = FungibleResourceCustodySupport.selectAtAccount(state.inventory().fungibleResources(), account.orElseThrow().id(),
                    record.ownerId(), fuel.itemKind(), 1);
            if (selected.isEmpty() || !FungibleResourceCustodySupport.canReserve(state, selected.orElseThrow())) continue;
            List<MaterialSourceSelection.Slice> source = hot ? MaterialSourceSelection.select(state.inventory().fungibleResources(),
                    account.orElseThrow().id(), fuel.itemKind(), selected.orElseThrow().lotQuantities()) : List.of();
            if (hot && (source.size() != 1 || !source.getFirst().address().equals(new PhysicalStackAddress.ContainerSlot(
                    new InventoryCustody.ContainerSlot(record.id(), station.fuelSlot())))))
                throw new IllegalArgumentException("station fuel is not held at its declared port");
            return Optional.of(new ProductionStationFuelBurn(stationId, record.id(), account.orElseThrow().id(), machine.revision(),
                    hot ? source.getFirst().epoch() : 0, fuel.itemKind(), selected.orElseThrow().lotQuantities(), fuel.heatUnits(), source));
        }
        return Optional.empty();
    }
    public static ContainerRecord record(FrontierWorldState state, SubjectId stationId) {
        return state.inventory().containers().values().stream().filter(record -> record.productionStation()
                .filter(spec -> spec.id().equals(stationId)).isPresent()).reduce((left, right) -> {
                    throw new IllegalArgumentException("duplicate installed station identity");
                }).orElseThrow(() -> new IllegalArgumentException("missing installed station identity"));
    }
    public static boolean pendingForContainer(FrontierWorldState state, SubjectId container) {
        return state.productionStations().machines().values().stream().flatMap(machine -> machine.pendingFuel().stream())
                .anyMatch(burn -> burn.containerId().equals(container));
    }
    public static FrontierWorldState apply(FrontierWorldState state, SubjectId subject, ProductionStationFuelChanged event) {
        var burn = event.burn(); var machine = state.productionStations().require(subject);
        if (!subject.equals(burn.stationId()) || machine.revision() != burn.machineRevision()
                || !record(state, subject).id().equals(burn.containerId()))
            throw new IllegalArgumentException("station fuel event has a foreign instance/revision");
        if (event.stage() == ProductionStationFuelChanged.Stage.HOT_PREPARED) {
            if (!next(state, subject).equals(Optional.of(burn))) throw new IllegalArgumentException("station fuel prepare has stale stock evidence");
            return state.withChanges(FrontierWorldStateUpdate.begin().productionStations(state.productionStations().with(machine.prepareFuel(burn))));
        }
        var ledger = state.inventory().fungibleResources();
        if (event.stage() == ProductionStationFuelChanged.Stage.COLD_CONSUMED) {
            if (!next(state, subject).equals(Optional.of(burn))) throw new IllegalArgumentException("station cold fuel use lacks exclusive current stock");
            ledger = ledger.destroy(burn.accountId(), burn.lots(), Map.of());
        } else {
            if (!machine.pendingFuel().equals(Optional.of(burn)) || !ReferenceContainerCustody.hasLiveCustody(state, burn.containerId())
                    || ReferenceContainerCustody.blocksCanonicalUse(state, burn.containerId()))
                throw new IllegalArgumentException("station fuel receipt lost its exact prepared physical boundary");
            var expected = ledger.bindings().values().stream().filter(binding -> binding.accountId().equals(burn.accountId())).map(binding -> {
                if (binding.authorityEpoch() != burn.authorityEpoch()) throw new IllegalArgumentException("station fuel has a stale physical epoch");
                int quantity = binding.quantity() - (binding.address().equals(burn.source().getFirst().address()) ? 1 : 0);
                return quantity == 0 ? Optional.<FungiblePhysicalObservation.Stack>empty() : Optional.of(
                        new FungiblePhysicalObservation.Stack(binding.address(), binding.itemKind(), quantity));
            }).flatMap(Optional::stream).toList();
            if (event.remaining().size() != expected.size() || !new HashSet<>(event.remaining()).equals(new HashSet<>(expected)))
                throw new IllegalArgumentException("station fuel observation changed unrelated inventory or lacks its exact decrement");
            ledger = ledger.consumeUnclaimedObserved(burn.accountId(), burn.authorityEpoch(), burn.lots(), event.remaining());
        }
        return state.withChanges(FrontierWorldStateUpdate.begin().inventory(state.inventory().withFungibleResources(ledger))
                .productionStations(state.productionStations().with(machine.fuelled(burn.heat()))));
    }
    private ProductionStationEnergy() { }
}

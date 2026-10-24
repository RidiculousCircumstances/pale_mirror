package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.persistence.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import io.farfrontier.palemirror.frontier.v3.process.FrontierWorldProcessCatalog;
import io.farfrontier.palemirror.frontier.v3.kernel.DeterministicProcessRegistry;
import static org.junit.jupiter.api.Assertions.*;

class AutomaticStationProductionTest {
    static FrontierWorldState initial() {
        return FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:smelting"), 125,
                FrontierRulesets.smeltingGraybox()));
    }
    static ContainerRecord station(FrontierWorldState state) {
        return state.inventory().containers().values().stream().filter(record -> record.productionStation()
                .filter(spec -> spec.capability() == ProductionStationSpec.Capability.SMELTING).isPresent())
                .sorted(Comparator.comparing(ContainerRecord::id)).findFirst().orElseThrow();
    }
    static FrontierWorldState supply(FrontierWorldState state, ContainerRecord record, String kind, int count, String suffix) {
        var account = FungibleResourceCustodySupport.accountAtContainer(state, record.id());
        var staging = new SubjectId("custody:smelting-" + suffix); var lot = new SubjectId("lot:smelting-" + suffix);
        var ledger = state.inventory().fungibleResources().issue(new ResourceLot(lot, record.ownerId(), kind, count, "fixture", List.of()),
                new CustodyAccount(staging, new ResourceCustody.Container(record.id()), Map.of(lot, count), Map.of()));
        if (account.isPresent()) ledger = ledger.transfer(staging, account.orElseThrow().id(), Map.of(lot, count), Map.of());
        return state.withInventory(state.inventory().withFungibleResources(ledger));
    }
    @Test void reserveAndInstalledEquipmentAreDeclaredWithoutFreeIron() {
        var state = initial(); var record = station(state);
        assertEquals(64, SettlementReservePlanning.read(state, record.ownerId(), 1).get("minecraft:iron_ingot").quantity());
        assertFalse(state.inventory().fungibleResources().lots().values().stream().anyMatch(lot -> lot.itemKind().equals("minecraft:iron_ingot")));
        for (var home : state.bootstrap().settlements()) {
            var facility = home.structures().stream().filter(value -> value.kind() == StructureKind.SMELTERY).findFirst().orElseThrow();
            var storage = state.inventory().containers().get(ProductionFacilityLayout.metalStorageId(facility));
            assertTrue(storage.storage().orElseThrow().filter().accepts("minecraft:raw_iron"));
            assertTrue(storage.storage().orElseThrow().filter().accepts("minecraft:iron_ingot"));
            assertFalse(storage.storage().orElseThrow().filter().accepts("minecraft:bread"));
            assertTrue(SettlementLocalCirculation.surfaceCells(home).contains(ProductionFacilityLayout.metalStorageStation(facility).support()));
        }
        assertEquals(state, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(state)));
        var spec = record.productionStation().orElseThrow();
        var recipe = state.bootstrap().ruleset().production().recipes().stream()
                .filter(r -> r.capability() == spec.capability()).findFirst().orElseThrow();
        assertTrue(AutomaticStationProduction.stations(state, record.ownerId(), recipe).contains(record));
        var conditions = new HashMap<>(state.structureConditions());
        conditions.put(spec.facilityId(), StructureCondition.DAMAGED);
        var damaged = state.withChanges(FrontierWorldStateUpdate.begin().structureConditions(conditions));
        assertFalse(AutomaticStationProduction.stations(damaged, record.ownerId(), recipe).contains(record),
                "changed facility condition must invalidate the installed-station index");
        assertTrue(AutomaticStationProduction.stations(state, record.ownerId(), recipe).contains(record));
    }
    @Test void outputReserveWithoutIngredientsNeitherRequestsNorBurnsFuel() {
        var state = initial(); var record = station(state); var spec = record.productionStation().orElseThrow();
        state = supply(state, record, "minecraft:coal", 1, "unbacked-fuel");
        assertEquals(0, StationFuelDemandComposition.heat(state, spec, 1));
        assertTrue(ProductionStationEnergy.next(state, spec.id()).isEmpty());
        var codec = new FrontierWorldStateCodec(); state = codec.decode(codec.encode(state));
        assertTrue(ProductionStationEnergy.next(state, spec.id()).isEmpty(), "recovery must not turn a reserve target into fuel use");
        state = supply(state, record, "minecraft:raw_iron", 1, "backed-input");
        assertEquals(200, StationFuelDemandComposition.heat(state, spec, 1));
        assertTrue(ProductionStationEnergy.next(state, spec.id()).isPresent());
    }
    @Test void partialInputCompletesWithoutWorkerAndSurvivesRecoveryWithExactFuelCost() {
        var state = initial(); var record = station(state); var spec = record.productionStation().orElseThrow();
        state = supply(state, record, "minecraft:raw_iron", 3, "input");
        assertTrue(AutomaticStationProduction.offer(state, spec.id(), 1).isEmpty(), "no fuel means no production");
        state = supply(state, record, "minecraft:coal", 1, "fuel");
        var burn = ProductionStationEnergy.next(state, spec.id()).orElseThrow();
        state = ProductionStationEnergy.apply(state, spec.id(), new ProductionStationFuelChanged(
                ProductionStationFuelChanged.Stage.COLD_CONSUMED, burn, List.of()));
        var registry = new DeterministicProcessRegistry(FrontierWorldProcessCatalog.descriptors(), FrontierWorldPayloadCodecs.create());
        var schedule = FrontierWorldProcessCatalog.initialSchedule(state.bootstrap()).stream()
                .filter(action -> action.kind().equals("frontier.production_station.review") && action.subject().equals(spec.id()))
                .findFirst().orElseThrow();
        var prepared = FrontierWorldProcessCatalog.prepareScheduled(registry, state, schedule, new SimInstant(1)).orElseThrow();
        assertEquals(FrontierWorldProcessCatalog.planScheduled(registry, state, schedule, new SimInstant(1)), prepared.prepare().get());
        var offer = AutomaticStationProduction.offer(state, spec.id(), 1).orElseThrow();
        assertEquals(3, offer.operation().outputQuantity());
        var start = new ProductionStationAutomaticStarted(spec.id(), state.productionStations().require(spec.id()).revision(), offer.operation().recipeId(), 1);
        var payloads = FrontierWorldPayloadCodecs.create(); assertEquals(start, payloads.decode(start.type(), payloads.encode(start)));
        state = AutomaticStationProduction.start(state, spec.id(), start);
        assertTrue(FrontierWorldProcessCatalog.prepareScheduled(registry, state, schedule, new SimInstant(2)).isEmpty(),
                "running machine progress must not wait for an economic worker round trip");
        var progress = ProductionStationProgressed.automatic(state, spec.id(), 2).orElseThrow();
        state = ProductionStationProgressed.apply(state, spec.id(), progress);
        assertEquals(600_000, state.productionStations().require(spec.id()).operation().orElseThrow().progress().orElseThrow().requiredMilliWork());
        var codec = new FrontierWorldStateCodec(); state = codec.decode(codec.encode(state));
        assertTrue(ProductionStationProgressed.automatic(state, spec.id(), 601).isEmpty());
        state = ProductionStationProgressed.apply(state, spec.id(), ProductionStationProgressed.automatic(state, spec.id(), 602).orElseThrow());
        var boundary = ProductionStationTransformation.next(state, spec.id()).orElseThrow();
        state = ProductionStationTransformation.apply(state, spec.id(), new ProductionStationRecipeChanged(
                ProductionStationRecipeChanged.Stage.COLD_COMPLETED, boundary, List.of()), ProductionOperationCompletionComposition::completed);
        assertTrue(state.productionStations().require(spec.id()).operation().isEmpty());
        assertEquals(1000, state.productionStations().require(spec.id()).storedHeat());
        var account = FungibleResourceCustodySupport.accountAtContainer(state, record.id()).orElseThrow();
        assertEquals(3, state.inventory().fungibleResources().unclaimedQuantity(account.id(), record.ownerId(), "minecraft:iron_ingot"));
        assertEquals(state, codec.decode(codec.encode(state)));
        assertTrue(AutomaticStationProduction.offer(state, spec.id(), 603).isEmpty(), "occupied output cannot be overwritten");
    }
    @Test void machineOutputIsPickupOnlyAndInputIsDeliveryOnly() {
        var state = initial(); var spec = station(state).productionStation().orElseThrow();
        var home = FrontierWorldStateSupport.settlement(state.bootstrap(), station(state).ownerId());
        var facility = home.structures().stream().filter(value -> value.id().equals(spec.facilityId())).findFirst().orElseThrow();
        var approach = SettlementWorkshopServicePort.forWorkshop(facility).exteriorApproach();
        var output = new ShipmentEndpoint.ProductionPort(home.id(), spec, ActorContainerItemOrder.StationPort.OUTPUT, approach);
        ResourceSourceComposition.admit(state, output);
        assertThrows(IllegalArgumentException.class, output::receivingPort);
        var input = new ShipmentEndpoint.ProductionPort(home.id(), spec, ActorContainerItemOrder.StationPort.INPUT, approach);
        assertThrows(IllegalArgumentException.class, () -> ResourceSourceComposition.admit(state, input));
    }
    @Test void fullReserveStopsAdmissionAndAnActualWithdrawalReopensDemand() {
        var state = initial(); var record = station(state); var spec = record.productionStation().orElseThrow();
        state = supply(state, record, "minecraft:raw_iron", 8, "reserve-input");
        state = supply(state, record, "minecraft:coal", 1, "reserve-fuel");
        state = ProductionStationEnergy.apply(state, spec.id(), new ProductionStationFuelChanged(
                ProductionStationFuelChanged.Stage.COLD_CONSUMED, ProductionStationEnergy.next(state, spec.id()).orElseThrow(), List.of()));
        var home = FrontierWorldStateSupport.settlement(state.bootstrap(), record.ownerId());
        var facility = home.structures().stream().filter(value -> value.id().equals(spec.facilityId())).findFirst().orElseThrow();
        var storage = state.inventory().containers().get(ProductionFacilityLayout.metalStorageId(facility));
        state = supply(state, storage, "minecraft:iron_ingot", 64, "reserve-full");
        assertEquals(0, ProductionStockDemand.read(state, home.id(), "minecraft:iron_ingot", 1).missing());
        assertTrue(AutomaticStationProduction.offer(state, spec.id(), 1).isEmpty());
        var account = FungibleResourceCustodySupport.accountAtContainer(state, storage.id()).orElseThrow();
        var lot = new SubjectId("lot:smelting-reserve-full");
        state = state.withInventory(state.inventory().withFungibleResources(state.inventory().fungibleResources()
                .destroy(account.id(), Map.of(lot, 3), Map.of())));
        assertEquals(3, AutomaticStationProduction.offer(state, spec.id(), 2).orElseThrow().operation().outputQuantity());
    }
    @Test void finiteIngotReserveCreatesRealIronExtractionDemandThroughSharedPlanner() {
        var state = initial();
        var deposits = state.extractionSites().deposits().values().stream()
                .filter(value -> value.site().layout().outputKinds().contains("minecraft:raw_iron")).toList();
        assertFalse(deposits.isEmpty());
        for (var deposit : deposits) {
            var home = deposit.site().settlementId();
            assertTrue(ProductionStockDemand.read(state, home, "minecraft:raw_iron", 1).requested(), "iron reserve must reach mining admission");
            assertTrue(ExtractionStockDemand.requested(state, deposit, Optional.empty(), 1));
        }
    }
}

package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.persistence.*;
import static io.farfrontier.palemirror.frontier.v3.process.AcceptedEconomyFixture.publish;
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
        return supply(state, record, ContainerSectionId.STOCK, kind, count, suffix);
    }
    static FrontierWorldState supply(FrontierWorldState state, ContainerRecord record, ContainerSectionId section, String kind, int count, String suffix) {
        var account = FungibleResourceCustodySupport.accountAtContainer(state, record.id(), section);
        var staging = new SubjectId("custody:smelting-" + suffix); var lot = new SubjectId("lot:smelting-" + suffix);
        var ledger = state.inventory().fungibleResources().issue(new ResourceLot(lot, record.ownerId(), kind, count, "fixture", List.of()),
                new CustodyAccount(staging, new ResourceCustody.Container(record.id(), section), Map.of(lot, count), Map.of()));
        if (account.isPresent()) ledger = ledger.transfer(staging, account.orElseThrow().id(), Map.of(lot, count), Map.of());
        return publish(state.withInventory(state.inventory().withFungibleResources(ledger)), record.ownerId(), 1);
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
    @Test void unlitFuelAndActiveCombustionHaveDistinctLifetimesAcrossRecovery() {
        var state = initial(); var record = station(state); var spec = record.productionStation().orElseThrow();
        state = supply(state, record, ContainerSectionId.FUEL, "minecraft:coal", 2, "fuel");
        assertTrue(ProductionStationEnergy.next(state, spec.id(), 1).isEmpty(), "idle machine never ignites stored fuel");
        state = supply(state, record, ContainerSectionId.INPUT, "minecraft:raw_iron", 3, "input");
        var offer = AutomaticStationProduction.offer(state, spec.id(), 1).orElseThrow();
        state = AutomaticStationProduction.start(state, spec.id(), new ProductionStationAutomaticStarted(spec.id(),
                state.productionStations().require(spec.id()).revision(), offer.operation().recipeId(), 1));
        var waiting = ProductionStationProgressed.automatic(state, spec.id(), 1).orElseThrow();
        state = ProductionStationProgressed.apply(state, spec.id(), waiting);
        assertFalse(state.productionStations().require(spec.id()).operation().orElseThrow().progress().orElseThrow().running());
        var burn = ProductionStationEnergy.next(state, spec.id(), 20).orElseThrow();
        var event = new ProductionStationFuelChanged(ProductionStationFuelChanged.Stage.COLD_CONSUMED, burn, List.of(), 20);
        var before = state;
        assertThrows(IllegalArgumentException.class, () -> ProductionStationEnergy.apply(before, spec.id(),
                new ProductionStationFuelChanged(ProductionStationFuelChanged.Stage.COLD_CONSUMED, burn, List.of(), 21)));
        state = ProductionStationEnergy.apply(state, spec.id(), event);
        var codec = new FrontierWorldStateCodec(); state = codec.decode(codec.encode(state));
        var fuelAccount = FungibleResourceCustodySupport.accountAtContainer(state, record.id(), ContainerSectionId.FUEL).orElseThrow();
        assertEquals(1, state.inventory().fungibleResources().unclaimedQuantity(fuelAccount.id(), record.ownerId(), "minecraft:coal"));
        state = ProductionStationProgressed.apply(state, spec.id(), ProductionStationProgressed.automatic(state, spec.id(), 620).orElseThrow());
        var boundary = ProductionStationTransformation.next(state, spec.id()).orElseThrow();
        state = ProductionStationTransformation.apply(state, spec.id(), new ProductionStationRecipeChanged(
                ProductionStationRecipeChanged.Stage.COLD_COMPLETED, boundary, List.of()), ProductionOperationCompletionComposition::completed);
        assertTrue(state.productionStations().require(spec.id()).operation().isEmpty());
        var output = FungibleResourceCustodySupport.accountAtContainer(state, record.id(), ContainerSectionId.OUTPUT).orElseThrow();
        assertEquals(3, state.inventory().fungibleResources().unclaimedQuantity(output.id(), record.ownerId(), "minecraft:iron_ingot"));
        assertEquals(1000, state.productionStations().require(spec.id()).burnRemaining(620));
        state = codec.decode(codec.encode(state));
        assertEquals(0, state.productionStations().require(spec.id()).burnRemaining(1620), "fire expires while idle");
        assertTrue(ProductionStationEnergy.next(state, spec.id(), 1620).isEmpty(), "idle expiry cannot ignite a second item");
        assertEquals(1, state.inventory().fungibleResources().unclaimedQuantity(fuelAccount.id(), record.ownerId(), "minecraft:coal"));
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
        state = supply(state, record, ContainerSectionId.INPUT, "minecraft:raw_iron", 8, "reserve-input");
        state = supply(state, record, ContainerSectionId.FUEL, "minecraft:coal", 1, "reserve-fuel");
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
        state = publish(state, home.id(), 2);
        assertEquals(3, AutomaticStationProduction.offer(state, spec.id(), 2).orElseThrow().operation().outputQuantity());
    }
    @Test void finiteIngotReserveCreatesRealIronExtractionDemandThroughSharedPlanner() {
        var state = initial();
        var deposits = state.extractionSites().deposits().values().stream()
                .filter(value -> value.site().layout().outputKinds().contains("minecraft:raw_iron")).toList();
        assertFalse(deposits.isEmpty());
        for (var deposit : deposits) {
            var home = deposit.site().settlementId();
            state = publish(state, home, 1);
            assertTrue(ProductionStockDemand.read(state, home, "minecraft:raw_iron", 1).requested(), "iron reserve must reach mining admission");
            assertTrue(ExtractionStockDemand.requested(state, deposit, Optional.empty(), 1));
        }
    }
}

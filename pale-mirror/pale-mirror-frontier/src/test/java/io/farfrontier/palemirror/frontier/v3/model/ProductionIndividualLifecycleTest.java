package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.persistence.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Actual station clock and accounting transitions; physical Minecraft effects have separate native coverage. */
class ProductionIndividualLifecycleTest {
    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-000000000228");
    private static final SubjectId MATERIAL = new SubjectId("lot:forge-test-ingots"), FUEL = new SubjectId("lot:forge-test-coal"),
            TOOL = new SubjectId("item:forge-test-target");
    record Fixture(FrontierWorldState state, ProductionStationSpec station, ProductionRecipe.Id recipe) { }
    private static Fixture fixture(boolean repair) {
        var state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:forge-test"), 41, FrontierRulesets.toolGraybox()));
        var record = state.inventory().containers().values().stream().filter(value -> value.productionStation()
                .filter(spec -> spec.capability() == ProductionStationSpec.Capability.FABRICATION).isPresent())
                .sorted(Comparator.comparing(ContainerRecord::id)).findFirst().orElseThrow();
        var station = record.productionStation().orElseThrow(); var account = ReferenceContainerCustody.scopeId(record.id());
        var ledger = state.inventory().fungibleResources().issue(new ResourceLot(FUEL, record.ownerId(), "minecraft:coal", 2, "fixture:coal", List.of()),
                new CustodyAccount(account, new ResourceCustody.Container(record.id()), Map.of(FUEL, 2), Map.of()));
        state = state.withInventory(state.inventory().withFungibleResources(ledger));
        state = ProductionStationEnergy.apply(state, station.id(), new ProductionStationFuelChanged(
                ProductionStationFuelChanged.Stage.COLD_CONSUMED, ProductionStationEnergy.next(state, station.id()).orElseThrow(), List.of()));
        var staging = new SubjectId("custody:forge-test-staging");
        ledger = state.inventory().fungibleResources().issue(new ResourceLot(MATERIAL, record.ownerId(), "minecraft:iron_ingot", 5, "fixture:metal", List.of()),
                new CustodyAccount(staging, new ResourceCustody.Container(record.id()), Map.of(MATERIAL, 5), Map.of()))
                .transfer(staging, account, Map.of(MATERIAL, 5), Map.of());
        ledger = ledger.rebind(account, 1, FungiblePhysicalObservation.bind(ledger, account, 1, List.of(
                ProductionStationOperatorTest.stack(station, station.inputSlot(), "minecraft:iron_ingot", 5),
                ProductionStationOperatorTest.stack(station, station.fuelSlot(), "minecraft:coal", 1))));
        var inventory = state.inventory().withFungibleResources(ledger);
        if (repair) inventory = inventory.store(new ExactItemStack(TOOL, record.ownerId(), "minecraft:iron_pickaxe", 1,
                new InventoryCustody.ContainerSlot(record.id(), station.maintenanceSlot().orElseThrow()), new ItemCondition(250, 200)));
        state = ReferenceContainerCustodyFixtures.observedAndHeld(state.withInventory(inventory), record.id());
        return new Fixture(state, station, new ProductionRecipe.Id(repair ? "pale_mirror:repair_iron_pickaxe" : "pale_mirror:make_iron_pickaxe"));
    }
    private static FrontierWorldState attend(FrontierWorldState state, Fixture fixture, ProductionStationOperatorChanged.Stage stage, long tick) {
        return ProductionStationOperator.apply(state, fixture.station().id(), new ProductionStationOperatorChanged(fixture.station().id(),
                state.productionStations().require(fixture.station().id()).revision(), PLAYER, fixture.recipe(), stage, tick));
    }
    private static FrontierWorldState finishWork(Fixture fixture) {
        var state = attend(fixture.state(), fixture, ProductionStationOperatorChanged.Stage.START, 200);
        for (long tick = 220; tick <= 360; tick += 20)
            state = attend(state, fixture, ProductionStationOperatorChanged.Stage.ATTEND, tick);
        assertTrue(state.productionStations().require(fixture.station().id()).operation().orElseThrow().complete());
        return state;
    }
    @Test void repairConsumesMaterialAndHeatKeepsIdentityAcrossPreparedRecoveryAndRejectsWrongOrRepeatedReceipt() {
        var f = fixture(true); var state = finishWork(f); var codec = new FrontierWorldStateCodec();
        var boundary = ProductionStationTransformation.next(state, f.station().id()).orElseThrow();
        assertEquals(TOOL, boundary.exactAfter().orElseThrow().id());
        assertEquals(75, boundary.exactAfter().orElseThrow().condition().damage());
        var prepared = new ProductionStationRecipeChanged(ProductionStationRecipeChanged.Stage.HOT_PREPARED, boundary, List.of());
        var payloads = FrontierWorldPayloadCodecs.create(); assertEquals(prepared, payloads.decode(prepared.type(), payloads.encode(prepared)));
        state = ProductionStationTransformation.apply(state, f.station().id(), prepared, ProductionOperationCompletionComposition::completed);
        var pending = codec.decode(codec.encode(state));
        assertEquals(200, pending.inventory().items().get(TOOL).condition().damage());
        assertThrows(IllegalArgumentException.class, () -> ProductionStationTransformation.apply(pending, f.station().id(),
                new ProductionStationRecipeChanged(ProductionStationRecipeChanged.Stage.HOT_COMPLETED, boundary, boundary.after(), boundary.exactBefore()),
                ProductionOperationCompletionComposition::completed));
        var completed = new ProductionStationRecipeChanged(ProductionStationRecipeChanged.Stage.HOT_COMPLETED, boundary, boundary.after(), boundary.exactAfter());
        var finished = ProductionStationTransformation.apply(pending, f.station().id(), completed, ProductionOperationCompletionComposition::completed);
        assertEquals(boundary.exactAfter().orElseThrow(), finished.inventory().items().get(TOOL));
        assertEquals(4, finished.inventory().fungibleResources().lots().get(MATERIAL).quantity());
        assertEquals(1500, finished.productionStations().require(f.station().id()).storedHeat());
        assertTrue(finished.productionStations().require(f.station().id()).operation().isEmpty());
        assertEquals(finished, codec.decode(codec.encode(finished)));
        assertThrows(IllegalArgumentException.class, () -> ProductionStationTransformation.apply(finished, f.station().id(), completed,
                ProductionOperationCompletionComposition::completed));
    }
    @Test void manufactureCompletesAfterHotToColdWithOneNewUsableItemAndNoFungibleToolLot() {
        var f = fixture(false); var state = finishWork(f); var account = ReferenceContainerCustody.scopeId(f.station().containerId());
        var lease = state.replicaCustody().custodyByScope().get(account);
        state = state.withChanges(FrontierWorldStateUpdate.begin().inventory(state.inventory().withFungibleResources(
                state.inventory().fungibleResources().releaseBindings(account, 1))).replicaCustody(state.replicaCustody()
                .release(account, 1, lease.expectedCanonicalRevision(), lease.expectedReplicaRevision())));
        var boundary = ProductionStationTransformation.next(state, f.station().id()).orElseThrow();
        assertEquals(0, boundary.authorityEpoch());
        var finished = ProductionStationTransformation.apply(state, f.station().id(), new ProductionStationRecipeChanged(
                ProductionStationRecipeChanged.Stage.COLD_COMPLETED, boundary, List.of()), ProductionOperationCompletionComposition::completed);
        var item = finished.inventory().items().get(boundary.exactAfter().orElseThrow().id());
        assertEquals(new ItemCondition(250, 0), item.condition());
        assertEquals(new InventoryCustody.ContainerSlot(f.station().containerId(), f.station().outputSlot()), item.custody());
        assertEquals(2, finished.inventory().fungibleResources().lots().get(MATERIAL).quantity());
        assertFalse(finished.inventory().fungibleResources().lots().containsKey(item.id()));
        assertEquals(1400, finished.productionStations().require(f.station().id()).storedHeat());
    }
    @Test void takingMaintenanceTargetCancelsOnlyThatOperationWithoutRefundOrToolReplacement() {
        var f = fixture(true); var state = attend(f.state(), f, ProductionStationOperatorChanged.Stage.START, 200);
        var tool = state.inventory().items().get(TOOL);
        var after = state.inventory().moveObservedItem(TOOL, tool.custody(), new InventoryCustody.Player(PLAYER));
        var interrupted = StationExactItemDeparture.observe(state, TOOL, tool.custody(), after);
        assertTrue(interrupted.productionStations().require(f.station().id()).operation().isEmpty());
        assertEquals(5, interrupted.inventory().fungibleResources().lots().get(MATERIAL).quantity());
        assertEquals(1500, interrupted.productionStations().require(f.station().id()).storedHeat());
        assertEquals(tool.withCustody(new InventoryCustody.Player(PLAYER)), interrupted.inventory().items().get(TOOL));
        var codec = new FrontierWorldStateCodec(); assertEquals(interrupted, codec.decode(codec.encode(interrupted)));
    }
}

package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.persistence.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Canonical operator/recipe recovery checks. Actual menu/drop interaction has separate native coverage. */
class ProductionStationOperatorTest {
    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-000000000321");
    private static final SubjectId WHEAT = new SubjectId("lot:operator-input"), COAL = new SubjectId("lot:operator-fuel");
    record Fixture(FrontierWorldState state, ProductionStationSpec station) { }
    static Fixture fixture() {
        var state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:station-operator"), 41,
                FrontierRulesets.fuelledQuarryGraybox()));
        var record = state.inventory().containers().values().stream().filter(value -> value.productionStation().isPresent())
                .sorted(Comparator.comparing(ContainerRecord::id)).findFirst().orElseThrow();
        var station = record.productionStation().orElseThrow(); var account = ReferenceContainerCustody.scopeId(record.id());
        var ledger = state.inventory().fungibleResources().issue(new ResourceLot(COAL, record.ownerId(), "minecraft:coal", 4, "fixture:coal", List.of()),
                new CustodyAccount(account, new ResourceCustody.Container(record.id()), Map.of(COAL, 4), Map.of()));
        state = state.withInventory(state.inventory().withFungibleResources(ledger));
        for (int burn = 0; burn < 2; burn++) state = ProductionStationEnergy.apply(state, station.id(), new ProductionStationFuelChanged(
                ProductionStationFuelChanged.Stage.COLD_CONSUMED, ProductionStationEnergy.next(state, station.id()).orElseThrow(), List.of()));
        ledger = state.inventory().fungibleResources().issue(new ResourceLot(WHEAT, record.ownerId(), "minecraft:wheat", 64, "fixture:input", List.of()),
                new CustodyAccount(new SubjectId("custody:operator-staging"), new ResourceCustody.Container(record.id()), Map.of(WHEAT, 64), Map.of()));
        ledger = ledger.transfer(new SubjectId("custody:operator-staging"), account, Map.of(WHEAT, 64), Map.of());
        ledger = ledger.rebind(account, 1, FungiblePhysicalObservation.bind(ledger, account, 1, List.of(
                stack(station, station.inputSlot(), "minecraft:wheat", 64), stack(station, station.fuelSlot(), "minecraft:coal", 2))));
        state = ReferenceContainerCustodyFixtures.observedAndHeld(state.withInventory(state.inventory().withFungibleResources(ledger)), record.id());
        return new Fixture(state, station);
    }
    static FungiblePhysicalObservation.Stack stack(ProductionStationSpec spec, int slot, String kind, int count) {
        return new FungiblePhysicalObservation.Stack(new PhysicalStackAddress.ContainerSlot(new InventoryCustody.ContainerSlot(spec.containerId(), slot)), kind, count);
    }
    private static FrontierWorldState observe(FrontierWorldState state, ProductionStationSpec station, ProductionStationOperatorChanged.Stage stage, long tick) {
        return ProductionStationOperator.apply(state, station.id(), new ProductionStationOperatorChanged(station.id(),
                state.productionStations().require(station.id()).revision(), PLAYER, BakeryRecipes.BREAD, stage, tick));
    }
    @Test void automaticClockDoesNotDependOnPlayerOrResidentAttendance() {
        var fixture = fixture(); var state = observe(fixture.state(), fixture.station(), ProductionStationOperatorChanged.Stage.START, 200);
        var manual = state.bootstrap().ruleset().production().require(BakeryRecipes.BREAD);
        var automatic = new ProductionRecipe(manual.id(), manual.capability(), manual.ingredient(), manual.product(),
                manual.heatPerBatch(), ProductionRecipe.ProcessingMode.AUTOMATIC);
        var catalog = new ProductionCatalog(List.of(automatic), state.bootstrap().ruleset().production().fuels(),
                state.bootstrap().ruleset().production().equipment());
        var operation = state.productionStations().require(fixture.station().id()).operation().orElseThrow();
        var work = state.bootstrap().ruleset().workCatalog();
        var running = ProductionStationClock.next(catalog, work, operation, 200, Optional.empty());
        assertTrue(running.running());
        var complete = ProductionStationClock.next(catalog, work, operation.withProgress(running), 1_800, Optional.empty());
        assertTrue(complete.complete());
        assertThrows(IllegalArgumentException.class, () -> ProductionStationClock.next(catalog, work, operation, 200,
                Optional.of(new ProductionStationClock.Attendance(1_000, 240))));
        var manualPaused = ProductionStationClock.next(state, operation, 1_000, Optional.empty());
        assertFalse(manualPaused.running());
        assertEquals(40_000L, manualPaused.completedMilliWork(), "manual lease credits only its 40 ticks, not absence");
    }
    @Test void playerAndNpcShareRecipeClockAndPreparedEffectWithoutLosingPartialInputOrFuel() {
        var fixture = fixture(); var station = fixture.station(); var state = observe(fixture.state(), station, ProductionStationOperatorChanged.Stage.START, 200);
        var codec = new FrontierWorldStateCodec(); assertEquals(state, codec.decode(codec.encode(state)));
        var operation = state.productionStations().require(station.id()).operation().orElseThrow();
        assertEquals(63, operation.inputQuantity()); assertEquals(21, operation.outputQuantity());
        state = observe(state, station, ProductionStationOperatorChanged.Stage.PAUSE, 210);
        state = codec.decode(codec.encode(state));
        var explicitPause = state;
        assertThrows(IllegalArgumentException.class, () -> observe(explicitPause, station, ProductionStationOperatorChanged.Stage.ATTEND, 1_000));
        var operatorCodecs = FrontierWorldPayloadCodecs.create();
        for (var stage : ProductionStationOperatorChanged.Stage.values()) {
            var event = new ProductionStationOperatorChanged(station.id(), state.productionStations().require(station.id()).revision(),
                    PLAYER, BakeryRecipes.BREAD, stage, 1_000);
            assertEquals(event, operatorCodecs.decode(event.type(), operatorCodecs.encode(event)));
        }
        assertThrows(IllegalArgumentException.class, () -> ProductionOperation.PlayerAttendanceIntent.fromWireTag(0));
        long beforeGap = state.productionStations().require(station.id()).operation().orElseThrow().progress().orElseThrow().completedMilliWork();
        state = observe(state, station, ProductionStationOperatorChanged.Stage.RESUME, 1_000);
        assertEquals(beforeGap, state.productionStations().require(station.id()).operation().orElseThrow().progress().orElseThrow().completedMilliWork());
        var expired = ProductionStationProgressed.automatic(state, station.id(), 1_060).orElseThrow();
        state = ProductionStationProgressed.apply(state, station.id(), expired);
        state = codec.decode(codec.encode(state));
        assertFalse(state.productionStations().require(station.id()).operation().orElseThrow().progress().orElseThrow().running());
        assertEquals(ProductionOperation.PlayerAttendanceIntent.RUN_REQUESTED,
                ((ProductionOperation.PlayerInitiator) state.productionStations().require(station.id()).operation().orElseThrow().initiator()).attendanceIntent());
        state = observe(state, station, ProductionStationOperatorChanged.Stage.ATTEND, 1_100);
        assertEquals(beforeGap + 40_000, state.productionStations().require(station.id()).operation().orElseThrow().progress().orElseThrow().completedMilliWork(),
                "reacquisition renews permitted work without crediting the physical absence");
        for (long at = 1_120, count = 0; !state.productionStations().require(station.id()).operation().orElseThrow().complete() && count < 1_000; at += 20, count++)
            state = observe(state, station, ProductionStationOperatorChanged.Stage.ATTEND, at);
        assertTrue(state.productionStations().require(station.id()).operation().orElseThrow().complete());
        var account = ((ProductionOperation.FungibleInput) operation.inputCustody()).accountId();
        var reacquired = state.withInventory(state.inventory().withFungibleResources(
                state.inventory().fungibleResources().releaseBindings(account, 1)));
        assertTrue(ProductionStationTransformation.next(reacquired, station.id()).isEmpty(),
                "completed work awaits material binding after container reacquisition");
        var boundary = ProductionStationTransformation.next(state, station.id()).orElseThrow();
        var prepared = new ProductionStationRecipeChanged(ProductionStationRecipeChanged.Stage.HOT_PREPARED, boundary, List.of());
        var wal = FrontierWorldPayloadCodecs.create(); assertEquals(prepared, wal.decode(prepared.type(), wal.encode(prepared)));
        state = ProductionStationTransformation.apply(state, station.id(), prepared, ProductionOperationCompletionComposition::completed);
        state = codec.decode(codec.encode(state)); var pending = state;
        assertTrue(pending.inventory().fungibleResources().lots().containsKey(WHEAT));
        assertThrows(IllegalArgumentException.class, () -> ProductionStationTransformation.apply(pending, station.id(),
                new ProductionStationRecipeChanged(ProductionStationRecipeChanged.Stage.HOT_COMPLETED, boundary, boundary.before()), ProductionOperationCompletionComposition::completed));
        var completed = new ProductionStationRecipeChanged(ProductionStationRecipeChanged.Stage.HOT_COMPLETED, boundary, boundary.after());
        var finished = ProductionStationTransformation.apply(state, station.id(), completed, ProductionOperationCompletionComposition::completed);
        assertTrue(finished.productionStations().require(station.id()).operation().isEmpty());
        assertEquals(1, finished.inventory().fungibleResources().lots().get(WHEAT).quantity());
        assertEquals(2, finished.inventory().fungibleResources().lots().get(COAL).quantity());
        assertEquals(21, finished.inventory().fungibleResources().lots().get(operation.outputId()).quantity());
        assertEquals(1520, finished.productionStations().require(station.id()).storedHeat());
        assertEquals(finished, codec.decode(codec.encode(finished)));
        assertThrows(IllegalArgumentException.class, () -> ProductionStationTransformation.apply(finished, station.id(), completed, ProductionOperationCompletionComposition::completed));
    }
    @Test void unclaimedRemainderCanLeaveButTakingReservedInputCancelsOnlyThatOperation() {
        var fixture = fixture(); var station = fixture.station(); var state = observe(fixture.state(), station, ProductionStationOperatorChanged.Stage.START, 200);
        var operation = state.productionStations().require(station.id()).operation().orElseThrow();
        var input = (ProductionOperation.FungibleInput) operation.inputCustody();
        assertTrue(SourceStockClaimLoss.canDepart(state, input.accountId(), "minecraft:wheat", 2));
        var claim = input.claims().keySet().iterator().next();
        state = SourceStockClaimLoss.settle(state, new FungibleStockDepartureObserved(input.accountId(), station.containerId(),
                state.inventory().containers().get(station.containerId()).ownerId(), 1, PLAYER, UUID.randomUUID(), Map.of(WHEAT, 2), Set.of(claim),
                List.of(stack(station, station.inputSlot(), "minecraft:wheat", 62), stack(station, station.fuelSlot(), "minecraft:coal", 2))));
        assertTrue(state.productionStations().require(station.id()).operation().isEmpty());
        assertFalse(state.inventory().fungibleResources().claims().containsKey(claim));
        assertFalse(state.inventory().fungibleResources().lots().containsKey(operation.outputId()));
        assertEquals(62, state.inventory().fungibleResources().lots().get(WHEAT).quantity());
        assertEquals(1520, state.productionStations().require(station.id()).storedHeat(), "disruption cannot refund already consumed fuel");
        var codec = new FrontierWorldStateCodec(); assertEquals(state, codec.decode(codec.encode(state)));
    }
}

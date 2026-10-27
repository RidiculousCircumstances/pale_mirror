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
        var station = record.productionStation().orElseThrow();
        var input = ContainerResourceAccounts.id(station.custody(ActorContainerItemOrder.StationPort.INPUT));
        var fuel = ContainerResourceAccounts.id(station.custody(ActorContainerItemOrder.StationPort.FUEL));
        var ledger = state.inventory().fungibleResources().issue(new ResourceLot(COAL, record.ownerId(), "minecraft:coal", 4, "fixture:coal", List.of()),
                new CustodyAccount(fuel, station.custody(ActorContainerItemOrder.StationPort.FUEL), Map.of(COAL, 4), Map.of()));
        ledger = ledger.issue(new ResourceLot(WHEAT, record.ownerId(), "minecraft:wheat", 64, "fixture:input", List.of()),
                new CustodyAccount(input, station.custody(ActorContainerItemOrder.StationPort.INPUT), Map.of(WHEAT, 64), Map.of()));
        ledger = ledger.rebind(input, 1, FungiblePhysicalObservation.bind(ledger, input, 1, List.of(stack(station, station.inputSlots().getFirst(), "minecraft:wheat", 64))));
        ledger = ledger.rebind(fuel, 1, FungiblePhysicalObservation.bind(ledger, fuel, 1, List.of(stack(station, station.fuelSlot().orElseThrow(), "minecraft:coal", 4))));
        state = ReferenceContainerCustodyFixtures.observedAndHeld(state.withInventory(state.inventory().withFungibleResources(ledger)), record.id());
        return new Fixture(state, station);
    }
    static FungiblePhysicalObservation.Stack stack(ProductionStationSpec spec, int slot, String kind, int count) {
        return new FungiblePhysicalObservation.Stack(new PhysicalStackAddress.ContainerSlot(new InventoryCustody.ContainerSlot(spec.containerId(), slot)), kind, count);
    }
    private static FrontierWorldState observe(FrontierWorldState state, ProductionStationSpec station, ProductionStationOperatorChanged.Stage stage, long tick) {
        var next = ProductionStationOperator.apply(state, station.id(), new ProductionStationOperatorChanged(station.id(),
                state.productionStations().require(station.id()).revision(), PLAYER, BakeryRecipes.BREAD, stage, tick));
        return stage == ProductionStationOperatorChanged.Stage.PAUSE ? next : ProductionCombustionFixtures.igniteHotIfNeeded(next, station.id(), tick);
    }
    @Test void idleAttendedMachineSleepsUntilItsOperationOwnerChangesAndRecoveryKeepsTheWait() {
        var fixture = fixture(); var station = fixture.station(); var before = fixture.state();
        var action = io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition
                .configuration(before.bootstrap()).initialSchedules().stream()
                .filter(value -> value.subject().equals(station.id()) && value.kind().equals("frontier.production_station.review"))
                .findFirst().orElseThrow();
        assertTrue(io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition.scheduledHeld(before, action));
        var proof = io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition.holdProof(before, action).orElseThrow();
        assertTrue(proof.stillHeld().test(before)); assertTrue(proof.wakeKeys().contains(station.id()));
        var codec = new FrontierWorldStateCodec();
        assertTrue(io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition.scheduledHeld(codec.decode(codec.encode(before)), action));
        var active = observe(before, station, ProductionStationOperatorChanged.Stage.START, 200);
        assertFalse(proof.stillHeld().test(active));
        assertFalse(io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition.scheduledHeld(active, action));
    }
    @Test void automaticClockDoesNotDependOnPlayerOrResidentAttendance() {
        var fixture = fixture(); var state = observe(fixture.state(), fixture.station(), ProductionStationOperatorChanged.Stage.START, 200);
        var manual = state.bootstrap().ruleset().production().require(BakeryRecipes.BREAD);
        var automatic = new ProductionRecipe(manual.id(), manual.capability(), manual.singleIngredient(), manual.product(), manual.requiresCombustion(), ProductionRecipe.ProcessingMode.AUTOMATIC);
        var catalog = new ProductionCatalog(List.of(automatic), state.bootstrap().ruleset().production().fuels(),
                state.bootstrap().ruleset().production().equipment());
        var operation = state.productionStations().require(fixture.station().id()).operation().orElseThrow();
        var work = state.bootstrap().ruleset().workCatalog();
        // A new automatic recipe starts a new clock; never reinterpret retained attended work.
        var machineOperation = new ProductionOperation(operation.id(), operation.stationId(), operation.orderId(), operation.recipeId(),
                operation.inputCustody(), operation.outputId(), operation.inputQuantity(), operation.outputQuantity(),
                Optional.empty(), false, operation.initiator(), Optional.empty());
        var running = ProductionStationClock.next(catalog, work, machineOperation, 200, Optional.empty(), Long.MAX_VALUE);
        assertTrue(running.running());
        long finish = 200 + work.require(automatic.product().itemKind(), WorkOperation.PRODUCE).workUnits()
                * (machineOperation.inputQuantity() / automatic.singleIngredient().quantity());
        var complete = ProductionStationClock.next(catalog, work, machineOperation.withProgress(running), finish, Optional.empty(), Long.MAX_VALUE);
        assertTrue(complete.complete());
        assertThrows(IllegalArgumentException.class, () -> ProductionStationClock.next(catalog, work, operation, 200,
                Optional.of(new ProductionStationClock.Attendance(1_000, 240)), Long.MAX_VALUE));
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
        assertTrue(finished.productionStations().require(station.id()).combustion().isPresent());
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
                List.of(stack(station, station.inputSlots().getFirst(), "minecraft:wheat", 62))));
        assertTrue(state.productionStations().require(station.id()).operation().isEmpty());
        assertFalse(state.inventory().fungibleResources().claims().containsKey(claim));
        assertFalse(state.inventory().fungibleResources().lots().containsKey(operation.outputId()));
        assertEquals(62, state.inventory().fungibleResources().lots().get(WHEAT).quantity());
        assertTrue(state.productionStations().require(station.id()).combustion().isPresent());
        var codec = new FrontierWorldStateCodec(); assertEquals(state, codec.decode(codec.encode(state)));
    }
}

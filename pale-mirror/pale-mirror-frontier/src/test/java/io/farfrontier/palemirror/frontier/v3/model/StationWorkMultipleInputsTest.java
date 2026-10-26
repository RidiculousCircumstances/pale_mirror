package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.process.*;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class StationWorkMultipleInputsTest {
    @Test void oneOrderAcquiresBothMaterialsAcrossRecoveredTripsAndProducesOneTool() {
        var fixture = started(); var state = fixture.state(); var job = fixture.job(); var station = fixture.station();
        var home = state.bootstrap().settlements().getFirst();
        var codec = new FrontierWorldStateCodec(); int loads = 0;
        for (long tick = 300, steps = 0; state.productionJobs().containsKey(job.id()) && steps < 800; tick += 20, steps++) {
            var current = state.productionJobs().get(job.id()); tick = BakeryTestClock.due(state, current, tick);
            var burn = ProductionStationEnergy.next(state, station.id(), tick);
            if (burn.isPresent()) state = ProductionStationEnergy.apply(state, station.id(), new ProductionStationFuelChanged(
                    ProductionStationFuelChanged.Stage.COLD_CONSUMED, burn.orElseThrow(), List.of(), tick));
            var proposed = ProductionProcess.planCompletion(state, ProductionProcess.complete(current, tick));
            var step = proposed.stream().map(ProposedEvent::payload).filter(BakeryColdStep.class::isInstance).map(BakeryColdStep.class::cast).findFirst();
            if (step.isEmpty()) continue;
            state = ProductionProcess.reduceBakeryColdStep(state, home.id(), step.orElseThrow());
            if (step.orElseThrow().action() == BakeryColdStep.Action.LOAD) { loads++; state = codec.decode(codec.encode(state)); }
        }
        assertFalse(state.productionJobs().containsKey(job.id()), "complete shared workflow must reach storage, not stop after the first material");
        assertEquals(2, loads);
        var tool = state.inventory().items().get(job.outputItemId());
        assertNotNull(tool); assertEquals("minecraft:iron_pickaxe", tool.itemKind());
        assertInstanceOf(InventoryCustody.ContainerSlot.class, tool.custody());
        assertEquals(0, state.inventory().fungibleResources().totalQuantity(home.id(), "minecraft:iron_ingot"));
        assertEquals(0, state.inventory().fungibleResources().totalQuantity(home.id(), "minecraft:stick"));
        assertTrue(state.inventory().fungibleResources().claims().isEmpty());
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void removedQueuedOrLoadedMaterialIsReplacedBeforeRecipeCanComplete(boolean afterFirstLoad) {
        var fixture = started(); var state = fixture.state(); var job = fixture.job(); var station = fixture.station();
        boolean removed = false, replaced = false; int loads = 0;
        for (long tick = 300, steps = 0; state.productionJobs().containsKey(job.id()) && steps < 1000; tick += 20, steps++) {
            var current = state.productionJobs().get(job.id()); var work = current.stationWork().orElseThrow();
            if (!removed && (!afterFirstLoad || loads == 1)) {
                var material = afterFirstLoad ? work.materials().loaded().getFirst() : null;
                var future = afterFirstLoad ? null : work.materials().queued().getFirst();
                var accountId = afterFirstLoad ? work.stationAccountId() : future.sourceAccount();
                var lots = afterFirstLoad ? material.lots() : future.lots();
                var claim = afterFirstLoad ? material.claimId() : future.claimId();
                var kind = afterFirstLoad ? material.itemKind() : future.itemKind();
                int quantity = lots.values().stream().mapToInt(Integer::intValue).sum();
                state = withdraw(state, accountId, lots, claim, kind);
                assertFalse(state.inventory().items().containsKey(job.outputItemId()), "loss cannot fabricate the product");
                var depot = state.inventory().containers().get(FrontierWorldState.depotId(job.settlementId()));
                state = AutomaticStationProductionTest.supply(state, depot, kind, quantity, "replacement-material");
                removed = true;
            }
            current = state.productionJobs().get(job.id()); tick = BakeryTestClock.due(state, current, tick);
            var burn = ProductionStationEnergy.next(state, station.id(), tick);
            if (burn.isPresent()) state = ProductionStationEnergy.apply(state, station.id(), new ProductionStationFuelChanged(
                    ProductionStationFuelChanged.Stage.COLD_CONSUMED, burn.orElseThrow(), List.of(), tick));
            var proposed = ProductionProcess.planCompletion(state, ProductionProcess.complete(current, tick));
            for (var event : proposed) {
                if (event.payload() instanceof BakeryInputReallocated replacement) {
                    state = ProductionProcess.reduceBakeryInputReallocated(state, job.settlementId(), replacement); replaced = true;
                } else if (event.payload() instanceof BakeryColdStep step) {
                    state = ProductionProcess.reduceBakeryColdStep(state, job.settlementId(), step);
                    if (step.action() == BakeryColdStep.Action.LOAD) loads++;
                }
            }
        }
        assertTrue(removed && replaced, "the exact interrupted order must replace the lost ingredient");
        assertFalse(state.productionJobs().containsKey(job.id()));
        assertEquals("minecraft:iron_pickaxe", state.inventory().items().get(job.outputItemId()).itemKind());
        assertTrue(state.inventory().fungibleResources().claims().isEmpty());
    }
    @Test void cancelledOrderCannotRestoreAnIngredientAlreadyTakenByAPlayer() {
        var fixture = started(); var job = fixture.job(); var work = job.stationWork().orElseThrow();
        var hold = (ProductionInputHold.FungibleCold) job.inputHold();
        var state = withdraw(fixture.state(), work.sourceAccountId(), hold.inputLots(), hold.claimId(), work.inputItemKind());
        state = state.cancelProductionJob(job.id());
        assertFalse(state.productionJobs().containsKey(job.id()));
        assertEquals(0, state.inventory().fungibleResources().totalQuantity(job.settlementId(), work.inputItemKind()));
        assertEquals(2, state.inventory().fungibleResources().totalQuantity(job.settlementId(), "minecraft:stick"));
        assertFalse(state.inventory().items().containsKey(job.outputItemId()));
        assertTrue(state.inventory().fungibleResources().claims().isEmpty());
        assertEquals(state, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(state)));
    }
    private static FrontierWorldState withdraw(FrontierWorldState state, SubjectId accountId, Map<SubjectId, Integer> lots,
            SubjectId claim, String kind) {
        return withdraw(state, accountId, lots, Set.of(claim), Set.of(kind));
    }
    private static FrontierWorldState withdraw(FrontierWorldState state, SubjectId accountId, Map<SubjectId, Integer> lots,
            Set<SubjectId> claims, Set<String> kinds) {
        var holder = (ResourceCustody.Container) state.inventory().fungibleResources().accounts().get(accountId).custody();
        var image = ReferenceContainerCustody.expectedFungibleSlots(state, holder.containerId()).entrySet().stream()
                .map(entry -> new FungiblePhysicalObservation.Stack(new PhysicalStackAddress.ContainerSlot(
                        new InventoryCustody.ContainerSlot(holder.containerId(), entry.getKey())), entry.getValue().itemKind(), entry.getValue().quantity())).toList();
        image = ContainerResourceAccounts.observed(state.inventory(), holder, image);
        var ledger = state.inventory().fungibleResources();
        state = ProductionResourceCustody.bind(state, accountId, 7, FungiblePhysicalObservation.bind(ledger, accountId, 7, image));
        assertTrue(SourceStockClaimLoss.admissibleClaims(state, accountId).containsAll(claims));
        var remaining = image.stream().filter(stack -> !kinds.contains(stack.itemKind())).toList();
        state = SourceStockClaimLoss.settle(state, accountId, 7, lots, claims, remaining);
        if (state.inventory().fungibleResources().accounts().containsKey(accountId))
            state = ProductionResourceCustody.release(state, accountId, 7);
        state = new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(state));
        return state;
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"minecraft:iron_ingot", "minecraft:stick", "both"})
    void processingInputLossCancelsOnceAndReleasesSurvivingIngredients(String removedKind) {
        var fixture = started(); var state = fixture.state(); var job = fixture.job(); long tick = 300;
        for (int step = 0; step < 800 && state.productionJobs().get(job.id()).stationWork().orElseThrow().phase() != StationWorkState.Phase.PROCESSING; step++) {
            var current = state.productionJobs().get(job.id()); tick = BakeryTestClock.due(state, current, tick + 20);
            var planned = ProductionProcess.planCompletion(state, ProductionProcess.complete(current, tick));
            for (var event : planned) if (event.payload() instanceof BakeryColdStep next)
                state = ProductionProcess.reduceBakeryColdStep(state, job.settlementId(), next);
        }
        var operation = state.productionStations().require(fixture.station().id()).operation().orElseThrow();
        var input = operation.inputCustody().materials().orElseThrow(); var ledger = state.inventory().fungibleResources();
        var claims = input.claims().keySet().stream().filter(id -> removedKind.equals("both") || ledger.claims().get(id).itemKind().equals(removedKind))
                .collect(java.util.stream.Collectors.toSet());
        assertFalse(claims.isEmpty());
        var lots = new TreeMap<SubjectId, Integer>(); var kinds = new HashSet<String>();
        for (var id : claims) { var claim = ledger.claims().get(id); kinds.add(claim.itemKind());
            claim.lotQuantities().forEach((lot, count) -> lots.merge(lot, count, Math::addExact)); }
        state = withdraw(state, input.accountId(), lots, claims, kinds);
        assertFalse(state.productionJobs().containsKey(job.id()));
        assertTrue(state.productionStations().require(fixture.station().id()).operation().isEmpty());
        assertTrue(state.inventory().fungibleResources().claims().isEmpty());
        assertFalse(state.inventory().items().containsKey(job.outputItemId()));
        int retained = FungibleResourceCustodySupport.accountAtContainer(state, fixture.station().containerId(), ContainerSectionId.INPUT)
                .map(account -> account.lotQuantities().values().stream().mapToInt(Integer::intValue).sum()).orElse(0);
        assertEquals(5 - lots.values().stream().mapToInt(Integer::intValue).sum(), retained, "surviving input remains real unclaimed stock");
        assertEquals(StrategicTaskStatus.BLOCKED, state.strategicPlans().tasks().get(job.taskId()).status());
        assertEquals(state, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(state)));
    }
    private record Started(FrontierWorldState state, ProductionJob job, ProductionStationSpec station) { }
    private static Started started() {
        var state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:multiple-input-order"), 41,
                FrontierRulesets.installed("frontier-v3-wood-production-graybox-r29")));
        var home = state.bootstrap().settlements().getFirst();
        var recipe = state.bootstrap().ruleset().production().require(new ProductionRecipe.Id("pale_mirror:make_iron_pickaxe"));
        var facility = home.structures().stream().filter(value -> value.kind() == StructureKind.FORGE).findFirst().orElseThrow();
        var station = ProductionFacilityLayout.station(facility);
        var depot = state.inventory().containers().get(FrontierWorldState.depotId(home.id()));
        state = AutomaticStationProductionTest.supply(state, depot, "minecraft:iron_ingot", 3, "order-metal");
        state = AutomaticStationProductionTest.supply(state, depot, "minecraft:stick", 2, "order-handles");
        state = AutomaticStationProductionTest.supply(state, state.inventory().containers().get(station.containerId()), ContainerSectionId.FUEL,
                "minecraft:coal", 2, "order-fuel");
        var objective = new StrategicObjective(new SubjectId("objective:multiple-input"), home.id(),
                StrategicObjectiveKind.SETTLEMENT_MAINTAIN_EQUIPMENT, Optional.empty(), 1, StrategicObjectiveStatus.ACTIVE);
        var task = new StrategicTask(new SubjectId("task:multiple-input"), objective.id(), home.id(), StrategicTaskKind.PRODUCE_AT_STATION,
                Optional.empty(), StrategicOperationSpecifications.requirements(objective.kind()), List.of(), StrategicTaskStatus.ACTIVE);
        state = state.withStrategicPlans(state.strategicPlans().addObjective(objective).addTask(task));
        var workerId = SettlementWorkPolicy.permissions(state, home.id()).workers(ResidentWorkKind.PRODUCTION).iterator().next();
        var worker = state.humanPopulation().resident(workerId);
        var bundle = ProductionMaterialSupply.select(state, home.id(), station, recipe, 1).orElseThrow();
        var job = StationWorkOrders.propose(state, task, facility, worker, ResidentWorkKind.PRODUCTION, recipe, bundle,
                Optional.empty(), ProductionRights.publicService(home.id()));
        assertEquals(1, job.stationWork().orElseThrow().materials().queued().size());
        state = ProductionProcess.reduceStarted(state, home.id(), new ProductionStarted(job, job.consumedItemId(),
                Optional.of(StationWorkAdmissionNavigation.propose(state, job))));
        return new Started(state, job, station);
    }

}

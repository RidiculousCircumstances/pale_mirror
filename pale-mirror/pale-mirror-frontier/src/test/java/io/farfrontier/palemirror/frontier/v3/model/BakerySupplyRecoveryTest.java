package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.model.supply.*;
import io.farfrontier.palemirror.frontier.v3.process.*;
import io.farfrontier.palemirror.frontier.v3.api.*;
import static io.farfrontier.palemirror.frontier.v3.process.AcceptedEconomyFixture.publish;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class BakerySupplyRecoveryTest {
    @Test void unburnedWarehouseFuelBacksAFullLoadButReservedFuelCannotBackAnotherOne() {
        var state = BakeryPartialBatchTest.fixture(63, FrontierRulesets.marketQuarryGraybox());
        var home = state.bootstrap().settlements().getFirst();
        var workshop = home.structures().stream().filter(s -> s.kind() == StructureKind.WORKSHOP).findFirst().orElseThrow();
        var station = ProductionStationSelection.usable(state, workshop.id(), BakeryRecipes.BREAD).orElseThrow();
        var machine = state.productionStations().require(station.id());
        state = state.withChanges(FrontierWorldStateUpdate.begin().productionStations(state.productionStations().with(
                new ProductionStations.Machine(machine.stationId(), 0, Optional.empty(), machine.revision(), Optional.empty()))));
        assertEquals(3, BakeryBatchSelection.available(state, home.id()).orElseThrow().quantity());
        var depot = FrontierWorldState.depotId(home.id());
        var account = ReferenceContainerCustody.scopeId(depot);
        var lot = new SubjectId("lot:unburned-bakery-fuel");
        var recipe = BakeryRecipes.bread(state);
        int fuel = Math.toIntExact(Math.ceilDiv(recipe.heatPerBatch() * 21,
                state.bootstrap().ruleset().production().requireFuel("minecraft:coal").heatUnits()));
        var staging = new SubjectId("custody:unburned-bakery-fuel");
        var ledger = state.inventory().fungibleResources().issue(new ResourceLot(lot, home.id(), "minecraft:coal", fuel,
                "fixture:warehouse-fuel", List.of()), new CustodyAccount(staging, new ResourceCustody.Container(depot),
                Map.of(lot, fuel), Map.of())).transfer(staging, account, Map.of(lot, fuel), Map.of());
        state = state.withInventory(state.inventory().withFungibleResources(ledger));
        assertEquals(63, BakeryBatchSelection.available(state, home.id()).orElseThrow().quantity(),
                "a cold furnace must not force a one-bread job while real warehouse fuel can supply the batch");
        assertFalse(ProductionStationProcessing.canStart(state, station.id(), recipe.id(), 63),
                "material backing does not burn fuel or authorize processing");
        assertEquals(ledger, state.inventory().fungibleResources(), "sizing must not consume or reserve resources");
        var claim = new ClaimAllocation(new SubjectId("claim:fuel-for-another-owner"), new SubjectId("work:other-fuel"),
                home.id(), "minecraft:coal", fuel, Map.of(lot, fuel), ClaimPurpose.EXTERNAL_RESERVATION);
        ledger = ledger.reserve(claim, account);
        state = state.withInventory(state.inventory().withFungibleResources(ledger));
        assertEquals(3, BakeryBatchSelection.available(state, home.id()).orElseThrow().quantity(),
                "a proposal must not borrow fuel already committed elsewhere");
    }

    @Test void retainedLargeLoadProcessesExecutablePortionAndPreservesReusableStationRemainder() {
        var state = BakeryPartialBatchTest.fixture(63, FrontierRulesets.marketQuarryGraybox());
        var task = state.strategicPlans().tasks().values().iterator().next();
        var home = FrontierWorldStateSupport.settlement(state.bootstrap(), task.ownerId());
        var workshop = home.structures().stream().filter(s -> s.kind() == StructureKind.WORKSHOP).findFirst().orElseThrow();
        var station = ProductionStationSelection.usable(state, workshop.id(), BakeryRecipes.BREAD).orElseThrow();
        var machine = state.productionStations().require(station.id());
        // Isolated preconditions model a retained large order and subsequently
        // scarce heat, as in the saved R178 counterexample. No live resource grant.
        state = state.withChanges(FrontierWorldStateUpdate.begin().productionStations(state.productionStations().with(
                new ProductionStations.Machine(machine.stationId(), 1680, Optional.empty(), machine.revision(), Optional.empty()))));
        state = publish(state, task.ownerId(), 200);
        var started = ProductionProcess.planStart(state, ProductionProcess.start(task, 200)).stream()
                .map(ProposedEvent::payload).filter(ProductionStarted.class::isInstance).map(ProductionStarted.class::cast).findFirst().orElseThrow();
        state = StrategicObjectiveProcess.reduceTaskTransition(state, task.ownerId(), new StrategicTaskTransition(task.id(), StrategicTaskStatus.ACTIVE));
        state = ProductionProcess.reduceStarted(state, task.ownerId(), started);
        var original = started.job();
        assertEquals(21, original.outputCount());
        machine = state.productionStations().require(original.stationWork().orElseThrow().stationId());
        state = state.withChanges(FrontierWorldStateUpdate.begin().productionStations(state.productionStations().with(
                new ProductionStations.Machine(machine.stationId(), 1600, Optional.empty(), machine.revision() + 1, Optional.empty()))));
        boolean loaded = false; var codec = new io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec();
        for (long due = 300, steps = 0; state.productionJobs().containsKey(original.id()) && steps < 700; due += 20, steps++) {
            var job = state.productionJobs().get(original.id()); due = BakeryTestClock.due(state, job, due);
            var step = ProductionProcess.planCompletion(state, ProductionProcess.complete(job, due)).stream()
                    .map(ProposedEvent::payload).filter(BakeryColdStep.class::isInstance).map(BakeryColdStep.class::cast).findFirst().orElseThrow();
            state = ProductionProcess.reduceBakeryColdStep(state, task.ownerId(), step);
            if (step.action() == BakeryColdStep.Action.LOAD) {
                loaded = true;
                assertEquals(20, state.productionJobs().get(original.id()).outputCount());
                assertEquals(0, state.productionStations().require(machine.stationId()).storedHeat());
                state = codec.decode(codec.encode(state));
            }
        }
        assertTrue(loaded); assertFalse(state.productionJobs().containsKey(original.id()));
        var remaining = BakeryBatchSelection.available(state, task.ownerId()).orElseThrow();
        assertEquals(3, remaining.quantity());
        assertEquals(ProductionStationEnergy.record(state, machine.stationId()).id(), remaining.sourceContainer(state));
        assertEquals(3, state.inventory().fungibleResources().totalQuantity(task.ownerId(), "minecraft:wheat"));
        assertEquals(20, state.inventory().fungibleResources().lots().get(original.outputItemId()).quantity());
        assertTrue(state.inventory().fungibleResources().claims().isEmpty());
        var priorObjective = state.strategicPlans().objectives().get(task.objectiveId());
        var objective = new StrategicObjective(new SubjectId("objective:station-remainder"), task.ownerId(), priorObjective.kind(),
                priorObjective.infectionTarget(), priorObjective.resourceSiteTarget(), priorObjective.decisionOrdinal() + 1,
                StrategicObjectiveStatus.ACTIVE, priorObjective.authorityId(), priorObjective.authorityEpoch());
        var successor = new StrategicTask(new SubjectId("task:station-remainder"), objective.id(), task.ownerId(),
                task.kind(), task.infectionTarget(), task.resourceSiteTarget(), task.requirements(), task.dependencies(),
                StrategicTaskStatus.PENDING, task.authorityId(), task.authorityEpoch());
        state = state.withChanges(FrontierWorldStateUpdate.begin().strategicPlans(state.strategicPlans().addObjective(objective).addTask(successor)));
        machine = state.productionStations().require(machine.stationId());
        state = state.withChanges(FrontierWorldStateUpdate.begin().productionStations(state.productionStations().with(
                new ProductionStations.Machine(machine.stationId(), 80, Optional.empty(), machine.revision() + 1, Optional.empty()))));
        state = publish(state, task.ownerId(), 20000);
        var next = ProductionProcess.planStart(state, ProductionProcess.start(successor, 20000)).stream()
                .map(ProposedEvent::payload).filter(ProductionStarted.class::isInstance).map(ProductionStarted.class::cast).findFirst().orElseThrow();
        state = StrategicObjectiveProcess.reduceTaskTransition(state, task.ownerId(), new StrategicTaskTransition(successor.id(), StrategicTaskStatus.ACTIVE));
        state = ProductionProcess.reduceStarted(state, task.ownerId(), next);
        assertEquals(next.job().stationWork().orElseThrow().stationAccountId(), next.job().stationWork().orElseThrow().sourceAccountId());
        for (long due = 20020, steps = 0; state.productionJobs().containsKey(next.job().id()) && steps < 700; due += 20, steps++) {
            var job = state.productionJobs().get(next.job().id()); due = BakeryTestClock.due(state, job, due);
            var step = ProductionProcess.planCompletion(state, ProductionProcess.complete(job, due)).stream()
                    .map(ProposedEvent::payload).filter(BakeryColdStep.class::isInstance).map(BakeryColdStep.class::cast).findFirst().orElseThrow();
            state = ProductionProcess.reduceBakeryColdStep(state, task.ownerId(), step);
        }
        assertFalse(state.productionJobs().containsKey(next.job().id()));
        assertEquals(0, state.inventory().fungibleResources().totalQuantity(task.ownerId(), "minecraft:wheat"));
        assertEquals(1, state.inventory().fungibleResources().lots().get(next.job().outputItemId()).quantity());
    }

    @Test void retainedBatchDeclaresFuelBeforeArrivalAndStopsDemandingItWhenStationIsSupplied() {
        var state = BakeryPartialBatchTest.fixture(63, FrontierRulesets.marketQuarryGraybox());
        var task = state.strategicPlans().tasks().values().iterator().next();
        state = publish(state, task.ownerId(), 200);
        var started = ProductionProcess.planStart(state, ProductionProcess.start(task, 200)).stream()
                .map(ProposedEvent::payload).filter(ProductionStarted.class::isInstance).map(ProductionStarted.class::cast)
                .findFirst().orElseThrow();
        state = StrategicObjectiveProcess.reduceTaskTransition(state, task.ownerId(), new StrategicTaskTransition(task.id(), StrategicTaskStatus.ACTIVE));
        state = ProductionProcess.reduceStarted(state, task.ownerId(), started);
        var job = started.job(); var station = job.stationWork().orElseThrow().stationId();
        var participant = state.companies().goodsTrade().participants().participants().get(task.ownerId());
        state = publish(state, task.ownerId(), 200);
        var publicPlan = GoodsParticipantAssessment.view(state, participant, 200L).supplyPlan().orElseThrow();
        assertTrue(publicPlan.require("minecraft:coal").urgent(100));
        assertTrue(publicPlan.require("minecraft:coal").protectedInput() > 0,
                "the actual settlement/market composition must retain the admitted batch's fuel obligation");
        assertTrue(publicPlan.require("minecraft:bread").purchase() >= job.outputCount(),
                "an admitted job without fuel must not hide food demand from the actual market policy");
        assertTrue(publicPlan.require("minecraft:bread").reasons().contains(SupplyPlanner.Reason.UNSECURED_PRODUCTION));
        var policy = new SupplyPolicy(500, 1, 8, 100, 500, true);
        var facts = Map.of("minecraft:bread", new SupplyPlanner.Stock(0, 0, job.outputCount(), job.outputCount(), 0, 1, 100, policy),
                "minecraft:coal", new SupplyPlanner.Stock(0, 0, 0, 0, 0, 0, 100, policy));
        var dependencies = SupplyCapacityComposition.dependencies(state, task.ownerId(), facts);
        assertFalse(dependencies.isEmpty());
        var plan = new SupplyPlanner().plan(facts, List.of(), dependencies);
        assertTrue(plan.require("minecraft:coal").urgent(100));
        assertTrue(plan.require("minecraft:coal").purchase() > 0);
        var record = ProductionStationEnergy.record(state, station);
        var account = ReferenceContainerCustody.scopeId(record.id());
        var lot = new SubjectId("lot:recovery-fuel");
        int quantity = Math.toIntExact(plan.require("minecraft:coal").purchase());
        var ledger = state.inventory().fungibleResources().issue(new ResourceLot(lot, task.ownerId(), "minecraft:coal", quantity,
                "fixture:recovery", List.of()), new CustodyAccount(account, new ResourceCustody.Container(record.id()), Map.of(lot, quantity), Map.of()));
        var supplied = state.withInventory(state.inventory().withFungibleResources(ledger));
        assertTrue(SupplyCapacityComposition.dependencies(supplied, task.ownerId(), facts).isEmpty());
        assertEquals(ProductionStationProcessing.StartReadiness.INSUFFICIENT_HEAT,
                ProductionStationProcessing.startReadiness(supplied, station, job.stationWork().orElseThrow().recipeId(), job.inputCount()),
                "fuel on hand is a supply promise, not already burned heat");

        // A player can destroy the selected station before the baker arrives.
        // The job retains its exact historical endpoint until owner replacement,
        // but its missing machine must not crash unrelated settlement planning.
        var lost = state.withInventory(state.inventory().withSurfaceStatus(record.id(), ContainerSurfaceStatus.PREPARED)
                .withSurfaceStatus(record.id(), ContainerSurfaceStatus.ACTIVE).withSurfaceStatus(record.id(), ContainerSurfaceStatus.RETIRED));
        lost = new ProductionStationRetired(station, lost.productionStations().require(station).revision()).apply(lost, station);
        assertEquals(0, ProductionStationEnergy.suppliedHeat(lost, station));
        assertFalse(SupplyCapacityComposition.dependencies(lost, task.ownerId(), facts).isEmpty());
        var lostDemand = GoodsParticipantAssessment.view(lost, participant, 200L).supplyPlan().orElseThrow().require("minecraft:bread");
        assertTrue(lostDemand.purchase() > 0 && lostDemand.production() == 0,
                "lost station must preserve actual food purchasing, not promise unavailable processing: " + lostDemand);
        assertEquals(state.productionJobs(), lost.productionJobs(), "forecasting cannot cancel or retarget the retained job");
        var codec = new io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec();
        var restored = codec.decode(codec.encode(lost));
        assertEquals(0, ProductionStationEnergy.suppliedHeat(restored, station));
    }
}

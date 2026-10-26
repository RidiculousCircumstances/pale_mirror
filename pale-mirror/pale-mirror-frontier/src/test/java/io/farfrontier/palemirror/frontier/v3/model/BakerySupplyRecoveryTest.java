package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.model.supply.*;
import io.farfrontier.palemirror.frontier.v3.process.*;
import io.farfrontier.palemirror.frontier.v3.api.*;
import static io.farfrontier.palemirror.frontier.v3.process.AcceptedEconomyFixture.publish;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class BakerySupplyRecoveryTest {
    @Test void batchSizingDoesNotRequirePrepaidFuelAndNeverConsumesOrBorrowsIt() {
        var state = BakeryPartialBatchTest.fixture(63, FrontierRulesets.marketQuarryGraybox());
        var home = state.bootstrap().settlements().getFirst();
        var workshop = home.structures().stream().filter(s -> s.kind() == StructureKind.WORKSHOP).findFirst().orElseThrow();
        var station = ProductionStationSelection.usable(state, workshop.id(), BakeryRecipes.BREAD).orElseThrow();
        var machine = state.productionStations().require(station.id());
        state = state.withChanges(FrontierWorldStateUpdate.begin().productionStations(state.productionStations().with(
                new ProductionStations.Machine(machine.stationId(), Optional.empty(), Optional.empty(), machine.revision(), Optional.empty()))));
        assertEquals(63, BakeryBatchSelection.available(state, home.id()).orElseThrow().quantity());
        var depot = FrontierWorldState.depotId(home.id());
        var account = ReferenceContainerCustody.scopeId(depot);
        var lot = new SubjectId("lot:unburned-bakery-fuel");
        var recipe = BakeryRecipes.bread(state);
        int fuel = Math.toIntExact(Math.ceilDiv(recipe.nominalBurnTicksPerBatch(state.bootstrap().ruleset().workCatalog()) * 21,
                state.bootstrap().ruleset().production().requireFuel("minecraft:coal").burnTicks()));
        var staging = new SubjectId("custody:unburned-bakery-fuel");
        var ledger = state.inventory().fungibleResources().issue(new ResourceLot(lot, home.id(), "minecraft:coal", fuel,
                "fixture:warehouse-fuel", List.of()), new CustodyAccount(staging, new ResourceCustody.Container(depot),
                Map.of(lot, fuel), Map.of())).transfer(staging, account, Map.of(lot, fuel), Map.of());
        state = state.withInventory(state.inventory().withFungibleResources(ledger));
        assertEquals(63, BakeryBatchSelection.available(state, home.id()).orElseThrow().quantity(),
                "a cold furnace must not force a one-bread job while real warehouse fuel can supply the batch");
        assertTrue(ProductionStationProcessing.canStart(state, station.id(), recipe.id(), 63),
                "installing an operation may precede ignition; sizing does not ignite or consume fuel");
        assertEquals(ledger, state.inventory().fungibleResources(), "sizing must not consume or reserve resources");
        var claim = new ClaimAllocation(new SubjectId("claim:fuel-for-another-owner"), new SubjectId("work:other-fuel"),
                home.id(), "minecraft:coal", fuel, Map.of(lot, fuel), ClaimPurpose.EXTERNAL_RESERVATION);
        ledger = ledger.reserve(claim, account);
        state = state.withInventory(state.inventory().withFungibleResources(ledger));
        assertEquals(63, BakeryBatchSelection.available(state, home.id()).orElseThrow().quantity(),
                "batch sizing is independent of fuel custody");
        assertEquals(claim, state.inventory().fungibleResources().claims().get(claim.id()),
                "sizing cannot consume fuel committed to another owner");
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
        var holder = record.productionStation().orElseThrow().custody(ActorContainerItemOrder.StationPort.FUEL);
        var account = ContainerResourceAccounts.id(holder);
        var lot = new SubjectId("lot:recovery-fuel");
        int quantity = Math.toIntExact(plan.require("minecraft:coal").purchase());
        var ledger = state.inventory().fungibleResources().issue(new ResourceLot(lot, task.ownerId(), "minecraft:coal", quantity,
                "fixture:recovery", List.of()), new CustodyAccount(account, holder, Map.of(lot, quantity), Map.of()));
        var supplied = state.withInventory(state.inventory().withFungibleResources(ledger));
        assertTrue(SupplyCapacityComposition.dependencies(supplied, task.ownerId(), facts).isEmpty());
        assertEquals(ProductionStationProcessing.StartReadiness.READY,
                ProductionStationProcessing.startReadiness(supplied, station, job.stationWork().orElseThrow().recipeId(), job.inputCount()),
                "complete input may be loaded before its fuel is ignited");

        // A player can destroy the selected station before the baker arrives.
        // The job retains its exact historical endpoint until owner replacement,
        // but its missing machine must not crash unrelated settlement planning.
        var lost = state.withInventory(state.inventory().withSurfaceStatus(record.id(), ContainerSurfaceStatus.PREPARED)
                .withSurfaceStatus(record.id(), ContainerSurfaceStatus.ACTIVE).withSurfaceStatus(record.id(), ContainerSurfaceStatus.RETIRED));
        lost = new ProductionStationRetired(station, lost.productionStations().require(station).revision()).apply(lost, station);
        assertEquals(0, ProductionStationEnergy.unlitFuelTicks(lost, station));
        assertFalse(SupplyCapacityComposition.dependencies(lost, task.ownerId(), facts).isEmpty());
        var lostDemand = GoodsParticipantAssessment.view(lost, participant, 200L).supplyPlan().orElseThrow().require("minecraft:bread");
        assertTrue(lostDemand.purchase() > 0 && lostDemand.production() == 0,
                "lost station must preserve actual food purchasing, not promise unavailable processing: " + lostDemand);
        assertEquals(state.productionJobs(), lost.productionJobs(), "forecasting cannot cancel or retarget the retained job");
        var codec = new io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec();
        var restored = codec.decode(codec.encode(lost));
        assertEquals(0, ProductionStationEnergy.unlitFuelTicks(restored, station));
    }
}

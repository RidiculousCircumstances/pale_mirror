package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.model.supply.*;
import io.farfrontier.palemirror.frontier.v3.process.*;
import io.farfrontier.palemirror.frontier.v3.api.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class BakerySupplyRecoveryTest {
    @Test void retainedBatchDeclaresFuelBeforeArrivalAndStopsDemandingItWhenStationIsSupplied() {
        var state = BakeryPartialBatchTest.fixture(63, FrontierRulesets.marketQuarryGraybox());
        var task = state.strategicPlans().tasks().values().iterator().next();
        var started = ProductionProcess.planStart(state, ProductionProcess.start(task, 200)).stream()
                .map(ProposedEvent::payload).filter(ProductionStarted.class::isInstance).map(ProductionStarted.class::cast)
                .findFirst().orElseThrow();
        state = StrategicObjectiveProcess.reduceTaskTransition(state, task.ownerId(), new StrategicTaskTransition(task.id(), StrategicTaskStatus.ACTIVE));
        state = ProductionProcess.reduceStarted(state, task.ownerId(), started);
        var job = started.job(); var station = job.bakeryWork().orElseThrow().stationId();
        var participant = state.companies().goodsTrade().participants().participants().get(task.ownerId());
        var publicPlan = GoodsParticipantView.read(state, participant).supplyPlan().orElseThrow();
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
                ProductionStationProcessing.startReadiness(supplied, station, job.bakeryWork().orElseThrow().recipeId(), job.inputCount()),
                "fuel on hand is a supply promise, not already burned heat");
    }
}

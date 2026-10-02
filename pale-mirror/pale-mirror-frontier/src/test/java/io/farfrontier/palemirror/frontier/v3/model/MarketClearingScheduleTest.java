package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.ProposedEvent;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect;
import io.farfrontier.palemirror.frontier.v3.process.MarketClearingProcess;
import io.farfrontier.palemirror.frontier.v3.process.ProductionProcess;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class MarketClearingScheduleTest {
    @Test
    void clearingRetainsTheExactMarketDemandWhileAuthorizedBakersNeedAnExecutableMeal() {
        FrontierWorldState initial = FrontierWorldState.initial(FrontierBootstrapper.create(
                new WorldId("frontier:market-baker-free-window"), 91L));
        SubjectId settlement = new SubjectId("settlement:1");
        StrategicObjective objective = new StrategicObjective(new SubjectId("objective:market-baker-free-window"), settlement,
                StrategicObjectiveKind.SETTLEMENT_PRODUCE_BREAD, Optional.empty(), 1, StrategicObjectiveStatus.ACTIVE);
        StrategicTask task = new StrategicTask(new SubjectId("task:market-baker-free-window"), objective.id(), settlement,
                StrategicTaskKind.PRODUCE_BREAD, Optional.empty(),
                List.of(StrategicTaskRequirement.ACTIVE_WORKSHOP, StrategicTaskRequirement.EXACT_WHEAT_INPUT),
                List.of(), StrategicTaskStatus.PENDING);
        FrontierWorldState pending = initial.withStrategicPlans(initial.strategicPlans().addObjective(objective).addTask(task));
        SubjectId depotAccount = ReferenceContainerCustody.scopeId(FrontierWorldState.depotId(settlement));
        var food = pending.inventory().fungibleResources().transformCold(depotAccount,
                java.util.Map.of(new SubjectId("lot:bootstrap-1-wheat"), 64), java.util.Map.of(),
                new ResourceLot(new SubjectId("lot:market-baker-food"), settlement, "minecraft:bread", 64,
                        "fixture:market-baker-food", List.of()));
        pending = pending.withInventory(pending.inventory().withFungibleResources(food));
        MarketDemand demand = MarketClearingProcess.foodDemand(pending, task, 43_000L);
        pending = pending.withCompanies(pending.companies().withMarket(MarketOrderBook.empty().open(demand)));
        var baker = FrontierWorldStateSupport.availableWorkResident(pending, settlement, ResidentProfession.BAKER).orElseThrow();
        assertFalse(ResidentActivityCoordinator.mayStartOrdinaryWork(pending, baker.id(), 43_000L));
        assertInstanceOf(ScheduleEffect.Rescheduled.class,
                ProductionProcess.planStart(pending, ProductionProcess.start(task, 43_000L)).getFirst().payload());

        List<ProposedEvent> planned = MarketClearingProcess.plan(pending, MarketClearingProcess.clear(demand, 1, 43_000L));

        assertEquals(1, planned.size());
        ScheduleEffect.Created retry = assertInstanceOf(ScheduleEffect.Created.class, planned.getFirst().payload());
        assertEquals("frontier.market.clear", retry.action().kind());
        assertEquals(43_000L + pending.bootstrap().ruleset().cadence().resourceHarvestRetryInterval(),
                retry.action().dueAt().ticks());
        assertEquals(demand.id(), retry.action().subject());
    }
}

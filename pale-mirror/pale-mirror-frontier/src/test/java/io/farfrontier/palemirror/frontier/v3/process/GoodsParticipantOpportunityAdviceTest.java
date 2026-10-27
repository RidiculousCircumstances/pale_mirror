package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.kernel.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.navigation.*;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GoodsParticipantOpportunityAdviceTest {
    @Test void stableChoiceMatchesDirectPlanningButCurrentFundingStillOwnsAdmission() {
        var state = quoted(); var buyer = buyer(state); var at = new SimInstant(401);
        var action = GoodsParticipantWakeup.action(buyer, at.ticks());
        var completion = GoodsParticipantProcess.planner().advice(state, action, at).orElseThrow().prepare();
        assertTrue(state.companies().goodsTrade().contracts().isEmpty());
        assertTrue(state.shipments().missions().isEmpty(), "advice never publishes its disposable draft");
        var events = completion.revalidate(state, at).orElseThrow().prepare().get();
        assertEquals(GoodsParticipantProcess.plan(state, action, at), events);
        assertTrue(events.stream().anyMatch(event -> event.payload() instanceof TransportMissionStarted));
        var money = state.inventory().economics();
        var drained = state.withInventory(state.inventory().withEconomics(money.transfer(buyer,
                new SubjectId("settlement:1"), money.availableToReserve(buyer))));
        var current = completion.revalidate(drained, at).orElseThrow().prepare().get();
        assertTrue(current.stream().noneMatch(event -> event.payload() instanceof GoodsTradeReserved
                || event.payload() instanceof TransportMissionStarted), "a dated choice cannot spend money that has left");
        var trade = state.companies().goodsTrade();
        for (var order : List.copyOf(trade.orders().values())) if (order.party().id().equals(new SubjectId("settlement:1")))
            trade = trade.withdraw(order.party().id(), new GoodsTradeOrderWithdrawn(order.id(), order.committedQuantity()));
        var withdrawn = state.withCompanies(state.companies().withGoodsTrade(trade));
        var afterWithdrawal = completion.revalidate(withdrawn, at).orElseThrow().prepare().get();
        assertTrue(afterWithdrawal.stream().noneMatch(event -> event.payload() instanceof GoodsTradeReserved
                || event.payload() instanceof TransportMissionStarted), "a dated choice cannot restore withdrawn seller consent");
    }
    @Test void pendingMarketChoiceNeverRestartsAlternativeSearchInTheExactPhase() throws Exception {
        var state = quoted(); var action = GoodsParticipantWakeup.action(buyer(state), 401); var at = new SimInstant(401);
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        PedestrianRoutePlanner pending = (geometry, start, target) -> {
            calls.incrementAndGet(); return new PedestrianRouteResult(PedestrianRouteResult.Status.PLANNING, List.of(), 0, 0, "PENDING_TEST_CALCULATION");
        };
        try (var binding = PedestrianRoutePlanning.bind(pending)) {
            var advice = GoodsParticipantProcess.planner().advice(state, action, at).orElseThrow().prepare();
            assertTrue(calls.get() > 0); int before = calls.get();
            var result = advice.revalidate(state, at).orElseThrow().prepare().get();
            assertEquals(before, calls.get(), "the global exact predecessor may not solve all alternatives again");
            assertTrue(result.stream().noneMatch(event -> event.payload() instanceof GoodsTradeReserved
                    || event.payload() instanceof TransportMissionStarted));
            assertTrue(result.stream().anyMatch(event -> event.payload() instanceof ScheduleEffect.Rescheduled));
        }
    }
    private static SubjectId buyer(FrontierWorldState state) {
        return state.companies().goodsTrade().participants().participants().get(new SubjectId("settlement:1")).known().getFirst().party().id();
    }
    private static FrontierWorldState quoted() {
        var state = ConsolidatedTradeFixture.initial(new WorldId("frontier:trade-choice-advice"), 20260918065L, false);
        for (var proposed : GoodsParticipantProcess.plan(state, GoodsParticipantProcess.review(new SubjectId("settlement:1"), 400), new SimInstant(400))) {
            if (proposed.payload() instanceof ScheduleEffect) continue;
            state = FrontierWorldRuntimeDefinition.reduce(state, new FrontierEvent(FrontierEvent.SCHEMA_VERSION,
                    new EventId("event:trade-choice-fixture"), new TransactionId("transaction:trade-choice-fixture"),
                    state.bootstrap().worldId(), new Revision(1), new SimInstant(400), proposed.subject(),
                    CauseChain.root(new CommandId("command:trade-choice-fixture")), proposed.payload()));
        }
        var participant = state.companies().goodsTrade().participants().participants().get(buyer(state));
        var policy = SettlementEconomicPlanPreparation.prepare(state, participant,
                SettlementEconomicEvaluation.domestic(state, participant, 401), 401);
        return SettlementEconomicPublication.apply(state, participant.party().id(), policy, 401);
    }
}

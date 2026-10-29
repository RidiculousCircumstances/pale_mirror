package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.kernel.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.navigation.*;
import io.farfrontier.palemirror.frontier.v3.model.geometry.*;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GoodsParticipantOpportunityAdviceTest {
    @Test void noncrossingQuoteSchedulesEconomicObservationWithoutAnImmediateMatchingSearch() {
        var state = ConsolidatedTradeFixture.initial(new WorldId("frontier:quote-notification"), 20260918065L, false);
        var participants = state.companies().goodsTrade().participants();
        var seller = participants.participants().get(new SubjectId("settlement:1"));
        var buyer = participants.participants().get(buyer(state));
        var bid = new GoodsTradeOrder(new SubjectId("order:notification-bid"), buyer.party(), GoodsTradeOrder.Side.BUY,
                FrontierWorldState.depotId(buyer.settlementId()), "minecraft:bread", 64, 0, FixedScalar.ONE, 1000);
        var before = state.withCompanies(state.companies().withGoodsTrade(state.companies().goodsTrade().place(bid)));
        var ask = new GoodsTradeOrder(new SubjectId("order:notification-ask"), seller.party(), GoodsTradeOrder.Side.SELL,
                FrontierWorldState.depotId(seller.settlementId()), "minecraft:bread", 64, 0, FixedScalar.whole(2), 1000);
        var changed = before.withCompanies(before.companies().withGoodsTrade(before.companies().goodsTrade().place(ask)));
        assertTrue(GoodsParticipantWakeup.quoted(changed, List.of(ask), 401).isEmpty());
        assertTrue(changed.companies().goodsTrade().participants().economicState(buyer.party().id()).inputRevision()
                > before.companies().goodsTrade().participants().economicState(buyer.party().id()).inputRevision());
        var affected = changed.companies().goodsTrade().participants().economicState(buyer.party().id()).affected();
        assertTrue(affected.containsKey(SettlementEconomicPlanningState.Change.MARKET));
        var scope = affected.get(SettlementEconomicPlanningState.Change.MARKET);
        assertTrue(scope.isEmpty() || scope.contains("minecraft:bread"),
                "price/production review must still observe an unaffordable ask; empty scope covers the whole participant");
        var cheaper = new GoodsTradeOrder(new SubjectId("order:notification-cheaper"), seller.party(), GoodsTradeOrder.Side.SELL,
                ask.containerId(), ask.itemKind(), 64, 0, FixedScalar.ONE, 1000);
        var crossing = changed.withCompanies(changed.companies().withGoodsTrade(changed.companies().goodsTrade().place(cheaper)));
        var events = GoodsParticipantWakeup.quoted(crossing, List.of(cheaper, cheaper), 401);
        assertEquals(1, events.size()); assertEquals(buyer.party().id(), events.getFirst().subject());
        assertEquals(GoodsParticipantWakeup.action(buyer.party().id(), 402),
                ((ScheduleEffect.ReconsiderationRequested) events.getFirst().payload()).action());
        assertTrue(crossing.companies().goodsTrade().contracts().isEmpty(), "notification grants no stock or contract");
    }
    @Test void economicPublicationDoesNotWaitForItsSuccessorCaravanRoute() throws Exception {
        var state = quoted(); var owner = buyer(state); var at = new SimInstant(402);
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        PedestrianRoutePlanner pending = (geometry, start, target) -> {
            calls.incrementAndGet();
            return new PedestrianRouteResult(PedestrianRouteResult.Status.PLANNING, List.of(), 0, 0, "PENDING_TEST_CALCULATION");
        };
        try (var binding = PedestrianRoutePlanning.bind(pending)) {
            var action = GoodsParticipantProcess.review(owner, at.ticks());
            var completion = GoodsParticipantProcess.planner().advice(state, action, at).orElseThrow().prepare();
            var events = completion.revalidate(state, at).orElseThrow().prepare().get();
            assertEquals(0, calls.get(), "neither forecasting nor exact publication may start caravan routing");
            assertTrue(events.stream().anyMatch(event -> event.payload() instanceof SettlementEconomicPlanPublished));
            assertTrue(events.stream().noneMatch(event -> event.payload() instanceof GoodsTradeReserved
                    || event.payload() instanceof TransportMissionStarted));
            var successor = events.stream().map(ProposedEvent::payload)
                    .filter(ScheduleEffect.ReconsiderationRequested.class::isInstance)
                    .map(ScheduleEffect.ReconsiderationRequested.class::cast).map(ScheduleEffect.ReconsiderationRequested::action)
                    .filter(next -> next.kind().equals(GoodsParticipantWakeup.OPPORTUNITY)).findFirst().orElseThrow();
            assertEquals(owner, successor.subject()); assertEquals(new SimInstant(403), successor.dueAt());
            for (var proposed : events) if (!(proposed.payload() instanceof ScheduleEffect))
                state = FrontierWorldRuntimeDefinition.reduce(state, new FrontierEvent(FrontierEvent.SCHEMA_VERSION,
                        new EventId("event:review-before-routing"), new TransactionId("transaction:review-before-routing"),
                        state.bootstrap().worldId(), new Revision(1), at, proposed.subject(),
                        CauseChain.root(new CommandId("command:review-before-routing")), proposed.payload()));
            var published = state.companies().goodsTrade().participants().economicState(owner).accepted().orElseThrow();
            assertEquals(at.ticks(), published.atTick());
            var codec = new io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec();
            state = codec.decode(codec.encode(state));
            var opportunity = GoodsParticipantProcess.planner().advice(state, successor, successor.dueAt()).orElseThrow().prepare();
            var waiting = opportunity.revalidate(state, successor.dueAt()).orElseThrow().prepare().get();
            assertTrue(calls.get() > 0, "the independent successor owns the still-pending route");
            assertTrue(waiting.stream().noneMatch(event -> event.payload() instanceof SettlementEconomicPlanPublished
                    || event.payload() instanceof GoodsTradeReserved || event.payload() instanceof TransportMissionStarted));
            assertEquals(published, state.companies().goodsTrade().participants().economicState(owner).accepted().orElseThrow());
        }
    }
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
        var waiting = new java.util.concurrent.atomic.AtomicBoolean(true);
        PedestrianRoutePlanner pending = (geometry, start, target) -> {
            calls.incrementAndGet();
            return waiting.get() ? new PedestrianRouteResult(PedestrianRouteResult.Status.PLANNING, List.of(), 0, 0, "PENDING_TEST_CALCULATION")
                    : PedestrianRoutePlanning.calculate(geometry, start, target);
        };
        try (var binding = PedestrianRoutePlanning.bind(pending)) {
            var captured = GoodsParticipantProcess.planner().advice(state, action, at).orElseThrow();
            var advice = captured.prepare();
            assertTrue(calls.get() > 0); int before = calls.get();
            var result = advice.revalidate(state, at).orElseThrow().prepare().get();
            assertEquals(before, calls.get(), "the global exact predecessor may not solve all alternatives again");
            assertTrue(result.stream().noneMatch(event -> event.payload() instanceof GoodsTradeReserved
                    || event.payload() instanceof TransportMissionStarted));
            assertTrue(result.stream().anyMatch(event -> event.payload() instanceof ScheduleEffect.Rescheduled));
            var observed = new NavigationGeometryObserved(state.navigationGeometry().revision(),
                    Map.of(new BlockPosition(0, 64, 0), NavigationObservation.known(NavigationCell.AIR)))
                    .apply(state, NavigationGeometryObserved.OWNER);
            assertTrue(advice.revalidate(observed, at).isEmpty(), "new terrain knowledge retires the pending input frame");
            assertEquals("GEOMETRY", advice.rejectionReason());
            var replacement = GoodsParticipantProcess.planner().advice(observed, action, at).orElseThrow().prepare();
            assertTrue(replacement.revalidate(observed, at).isPresent(), "a fresh capture can use the new geometry epoch");
            waiting.set(false);
            var resumed = captured.prepare().revalidate(state, at).orElseThrow().prepare().get();
            assertTrue(resumed.stream().anyMatch(event -> event.payload() instanceof TransportMissionStarted),
                    "the suspended market choice must reach an admitted successor when actual routes become available");
            assertEquals(GoodsParticipantProcess.plan(state, action, at), resumed);
            assertTrue(state.shipments().missions().isEmpty(), "resuming an advisory calculation never publishes its draft");
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

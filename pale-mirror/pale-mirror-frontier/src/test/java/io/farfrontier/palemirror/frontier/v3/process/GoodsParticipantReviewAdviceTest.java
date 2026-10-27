package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class GoodsParticipantReviewAdviceTest {
    @Test void unchangedProcurementResumeRetainsMeasuredRankingButCannotCarryConsentIntoChangedInputs() {
        var state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:comparison-continuation"), 126L,
                FrontierRulesets.installed("frontier-v3-simulation-baseline-r31")));
        var buyer = state.companies().goodsTrade().participants().participants().get(state.bootstrap().settlements().getFirst().id());
        var other = state.companies().goodsTrade().participants().participants().get(state.bootstrap().settlements().get(1).id());
        var a = new SubjectId("procurement:comparison-a"); var b = new SubjectId("procurement:comparison-b");
        var goods = Map.of("minecraft:bread", new io.farfrontier.palemirror.frontier.v3.model.market.ProcurementSelection.Line(64, FixedScalar.ONE));
        var preliminary = new io.farfrontier.palemirror.frontier.v3.model.market.ProcurementSelection.Offer(a, goods, FixedScalar.ONE,
                100, 1, io.farfrontier.palemirror.frontier.v3.model.market.ProcurementSelection.Departure.READY);
        var measured = new io.farfrontier.palemirror.frontier.v3.model.market.ProcurementSelection.Offer(a, goods, FixedScalar.whole(30),
                150, 30, io.farfrontier.palemirror.frontier.v3.model.market.ProcurementSelection.Departure.READY);
        var alternative = new io.farfrontier.palemirror.frontier.v3.model.market.ProcurementSelection.Offer(b, goods, FixedScalar.whole(10),
                120, 10, io.farfrontier.palemirror.frontier.v3.model.market.ProcurementSelection.Departure.READY);
        var timing = new io.farfrontier.palemirror.frontier.v3.model.expedition.ExpeditionTiming(100, 200);
        var facts = GoodsTripComparisonFacts.forDecision(state, buyer, 4000);
        facts.remember(measured, new io.farfrontier.palemirror.frontier.v3.model.expedition.ExpeditionTiming(150, 300));
        var resumed = GoodsTripComparisonFacts.forDecision(state, buyer, 4000);
        var requirement = List.of(new io.farfrontier.palemirror.frontier.v3.model.market.ProcurementSelection.Requirement("minecraft:bread", 64, 1000));
        var selector = new io.farfrontier.palemirror.frontier.v3.model.market.ProcurementSelection();
        assertEquals(a, selector.select(requirement, List.of(preliminary, alternative)).orElseThrow());
        assertEquals(b, selector.select(requirement, List.of(resumed.rank(preliminary, timing).offer(), alternative)).orElseThrow(),
                "resuming after a second route wait cannot make the first, already measured expensive option optimistically cheap again");
        assertEquals(preliminary, GoodsTripComparisonFacts.forDecision(state, buyer, 4001).rank(preliminary, timing).offer());
        assertEquals(preliminary, GoodsTripComparisonFacts.forDecision(state, other, 4000).rank(preliminary, timing).offer());
        var changed = state.withInventory(state.inventory().withEconomics(state.inventory().economics().transfer(
                buyer.party().id(), other.party().id(), FixedScalar.ONE)));
        assertEquals(preliminary, GoodsTripComparisonFacts.forDecision(changed, buyer, 4000).rank(preliminary, timing).offer(),
                "changed immutable funding inputs require a new comparison");
        assertTrue(state.companies().goodsTrade().contracts().isEmpty());
        assertTrue(state.shipments().missions().isEmpty(), "a measured comparison never reserves or dispatches a trip");
    }
    @Test void capturedForecastRejectsChangedOwnerInputsAndExpiryButAllowsDatedExternalObservations() {
        var state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:advice-proof"), 126L,
                FrontierRulesets.installed("frontier-v3-simulation-baseline-r31")));
        var home = state.bootstrap().settlements().get(6).id();
        var action = GoodsParticipantProcess.review(home, 4000); var at = new SimInstant(4000);
        var completion = io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition.configuration(state.bootstrap())
                .scheduledPlanner().advice(state, action, at).orElseThrow().prepare();
        assertTrue(completion.revalidate(state, new SimInstant(4001)).isPresent());
        var resident = state.humanPopulation().residents().values().iterator().next();
        var body = state.actorLocations().get(resident.id()).body();
        assertTrue(completion.revalidate(state.withActorBody(resident.id(), new BodyPosition(body.x() + 1, body.y(), body.z())),
                new SimInstant(4001)).isPresent(), "conditional economic advice is not a body-position or route authority");
        var trade = state.companies().goodsTrade(); var registry = trade.participants();
        var invalidated = registry.withEconomicState(home, registry.economicState(home).invalidate(
                Map.of(SettlementEconomicPlanningState.Change.CASH, Set.of())));
        assertTrue(completion.revalidate(state.withCompanies(state.companies().withGoodsTrade(trade.withParticipants(invalidated))), at).isEmpty());
        assertEquals("OWNER_INPUT", completion.rejectionReason(), "registered decorators must retain the owner's diagnosis");
        var marketOnly = registry.withEconomicState(home, registry.economicState(home).invalidate(
                Map.of(SettlementEconomicPlanningState.Change.MARKET, Set.of("minecraft:bread"))));
        var marketState = state.withCompanies(state.companies().withGoodsTrade(trade.withParticipants(marketOnly)));
        var refreshed = completion.revalidate(marketState, at).orElseThrow().prepare().get();
        assertTrue(refreshed.stream().anyMatch(event -> event.payload() instanceof SettlementEconomicPlanPublished),
                "an incoming quote may use the dated forecast but must still publish a valid current input envelope");
        var hardAfterMarket = marketOnly.withEconomicState(home, marketOnly.economicState(home).invalidate(
                Map.of(SettlementEconomicPlanningState.Change.CASH, Set.of())));
        assertTrue(completion.revalidate(state.withCompanies(state.companies().withGoodsTrade(trade.withParticipants(hardAfterMarket))), at).isEmpty(),
                "a market notification must not conceal changed actual funding");
        var owner = registry.participants().get(home); var peer = registry.participants().get(owner.known().getFirst().party().id());
        var peerChanged = registry.withEconomicState(peer.party().id(), registry.economicState(peer.party().id()).invalidate(
                Map.of(SettlementEconomicPlanningState.Change.STOCK, Set.of("minecraft:bread"))));
        assertTrue(completion.revalidate(state.withCompanies(state.companies().withGoodsTrade(trade.withParticipants(peerChanged))), at).isPresent(),
                "a peer's dated stock observation is not a promise of current supply");
        var peerPlan = SettlementEconomicPlanPreparation.prepare(state, peer,
                SettlementEconomicEvaluation.domestic(state, peer, 4000), 4000);
        var peerReviewed = SettlementEconomicPublication.apply(state, peer.party().id(), peerPlan, 4000);
        assertTrue(completion.revalidate(peerReviewed, at).isPresent(),
                "an unrelated buyer publication must not serialize every settlement's advice");
        var price = new io.farfrontier.palemirror.frontier.v3.model.market.MarketPricePolicy.Quote(
                FixedScalar.ONE, FixedScalar.ONE, FixedScalar.ONE, 4000, 0, Optional.empty(),
                io.farfrontier.palemirror.frontier.v3.model.market.StockSalePlan.retained());
        var repriced = registry.prices(peer.party().id(), new GoodsParticipantPricesReviewed(peer.reviewRevision(), 4000,
                Map.of("minecraft:bread", price)));
        assertTrue(completion.revalidate(state.withCompanies(state.companies().withGoodsTrade(trade.withParticipants(repriced))), at).isPresent(),
                "a peer price observation is not actual consent or a guarantee of current supply");
        var ownRepriced = registry.prices(home, new GoodsParticipantPricesReviewed(owner.reviewRevision(), 4000,
                Map.of("minecraft:bread", price)));
        assertTrue(completion.revalidate(state.withCompanies(state.companies().withGoodsTrade(trade.withParticipants(ownRepriced))), at).isEmpty(),
                "the owner's current published consent must not be replaced by stale advice");
        assertTrue(completion.revalidate(state, new SimInstant(4000 + state.bootstrap().ruleset().goodsTrade().reviewInterval())).isEmpty());
        assertTrue(completion.revalidate(state, new SimInstant(3999)).isEmpty());
    }
    @Test void publicQuoteWakesOpposingCommodityInterestsOnceAndLeavesOtherKnownPeersAlone() {
        var state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:addressed-market-wake"), 126L,
                FrontierRulesets.installed("frontier-v3-simulation-baseline-r31")));
        var owner = state.bootstrap().settlements().getFirst().id();
        var recipient = state.bootstrap().settlements().get(1).id();
        var order = new GoodsTradeOrder(new SubjectId("order:addressed-wake"),
                new GoodsTradeParty(owner, EconomicOwnerKind.SETTLEMENT_TREASURY),
                GoodsTradeOrder.Side.SELL, FrontierWorldState.depotId(owner), "minecraft:bread", 64, 0, FixedScalar.whole(1), 5000);
        var bid = new GoodsTradeOrder(new SubjectId("order:public-bid"),
                new GoodsTradeParty(recipient, EconomicOwnerKind.SETTLEMENT_TREASURY), GoodsTradeOrder.Side.BUY,
                FrontierWorldState.depotId(recipient), "minecraft:bread", 64, 0, FixedScalar.whole(2), 5000);
        state = GoodsTradeStateSupport.place(state, recipient, bid, 4000);
        var unrelated = state.bootstrap().settlements().get(2).id();
        state = GoodsTradeStateSupport.place(state, unrelated, new GoodsTradeOrder(new SubjectId("order:unrelated-coal-bid"),
                new GoodsTradeParty(unrelated, EconomicOwnerKind.SETTLEMENT_TREASURY), GoodsTradeOrder.Side.BUY,
                FrontierWorldState.depotId(unrelated), "minecraft:coal", 64, 0, FixedScalar.whole(2), 5000), 4000);
        assertEquals(1, GoodsParticipantProcess.reviewWorkUnits(state, GoodsParticipantWakeup.action(recipient, 4001)),
                "known peers without public supply are not charged as procurement alternatives");
        state = GoodsTradeStateSupport.place(state, owner, order, 4000);
        assertEquals(List.of(owner), GoodsOrderMatching.sellers(state,
                state.companies().goodsTrade().participants().participants().get(recipient), 4000)
                .stream().map(value -> value.party().id()).toList());
        var events = GoodsParticipantWakeup.quoted(state, List.of(order, order), 4000);
        assertEquals(List.of(recipient), events.stream().map(ProposedEvent::subject).toList(),
                "public asks notify only opposing commodity interest, not every known settlement");
        assertEquals(GoodsParticipantWakeup.action(recipient, 4001),
                ((io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect.ReconsiderationRequested) events.getFirst().payload()).action());
    }

    @Test void tripQuoteEnvelopeSharesOrderRemaindersWithoutReservingItsUnbackedStock() {
        var state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:trip-quote-envelope"), 126L,
                FrontierRulesets.installed("frontier-v3-simulation-baseline-r31")));
        var participants = state.companies().goodsTrade().participants().participants();
        var seller = participants.get(state.bootstrap().settlements().getFirst().id());
        var buyer = participants.get(state.bootstrap().settlements().get(1).id());
        var source = FrontierWorldState.depotId(seller.settlementId());
        var receiver = FrontierWorldState.depotId(buyer.settlementId());
        state = GoodsTradeStateSupport.place(state, seller.party().id(), new GoodsTradeOrder(new SubjectId("order:quote-envelope-ask"),
                seller.party(), GoodsTradeOrder.Side.SELL, source, "minecraft:coal", 70, 0, FixedScalar.ONE, 5000), 4000);
        for (int i = 0; i < 2; i++) state = GoodsTradeStateSupport.place(state, buyer.party().id(), new GoodsTradeOrder(
                new SubjectId("order:quote-envelope-bid-" + i), buyer.party(), GoodsTradeOrder.Side.BUY, receiver,
                "minecraft:coal", 60, 0, FixedScalar.whole(2), 5000), 4000);
        var inventory = state.inventory(); var trade = state.companies().goodsTrade();
        var lines = GoodsTripQuotes.lines(state, seller, buyer, new GoodsOrderMatching.Endpoints(source, receiver), 4000);
        assertEquals(70, lines.get("minecraft:coal").quantity(), "two bids cannot duplicate the one public ask");
        assertSame(inventory, state.inventory()); assertSame(trade, state.companies().goodsTrade());
        assertTrue(trade.contracts().isEmpty(), "a quote envelope is not backed or admitted commercial authority");
        assertTrue(GoodsTripQuotes.lines(state, seller, buyer, new GoodsOrderMatching.Endpoints(source, receiver), 5001).isEmpty(),
                "expired consent cannot remain a preliminary alternative");
    }

    @Test void marketOpportunityUsesPublishedConsentWithoutRenewingTheEconomicForecast() {
        var rules = FrontierRulesets.installed("frontier-v3-simulation-baseline-r31");
        var config = io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition.configuration(
                new WorldId("frontier:market-wake"), 126L, rules);
        var initial = config.initialState(); var home = initial.bootstrap().settlements().get(6).id();
        var owner = initial.companies().goodsTrade().participants().participants().get(home);
        var plan = SettlementEconomicPlanPreparation.prepare(initial, owner,
                SettlementEconomicEvaluation.domestic(initial, owner, 4000), 4000);
        var planned = SettlementEconomicPublication.apply(initial, home, plan, 4000);
        var trade = planned.companies().goodsTrade(); var registry = trade.participants();
        var market = planned.withCompanies(planned.companies().withGoodsTrade(trade.withParticipants(
                registry.withEconomicState(home, registry.economicState(home).invalidate(
                    Map.of(SettlementEconomicPlanningState.Change.MARKET, Set.of("minecraft:bread")))))));
        var reviews = SettlementEconomicReviewRequests.changed(planned, market, 4001);
        assertEquals(List.of(GoodsParticipantProcess.review(home, plan.reviewAtTick())), reviews,
                "an external quote must not advance the whole-economy deadline");
        var wake = ((io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect.ReconsiderationRequested)
                GoodsParticipantWakeup.party(market, home, "new-quote", 4001).getFirst().payload()).action();
        assertEquals(GoodsParticipantWakeup.OPPORTUNITY, wake.kind());
        assertNotEquals(reviews.getFirst().id(), wake.id());
        assertTrue(config.scheduledPlanner().advice(market, wake, new SimInstant(4002)).isEmpty());
        var events = config.scheduledPlanner().plan(market, wake, new SimInstant(4002));
        assertFalse(events.isEmpty());
        assertTrue(events.stream().noneMatch(event -> event.payload() instanceof SettlementEconomicPlanPublished
                || event.payload() instanceof GoodsParticipantPricesReviewed || event.payload() instanceof GoodsParticipantReviewed),
                "current-plan matching must not invoke or republish the economic solver");
        var hard = planned.withCompanies(planned.companies().withGoodsTrade(trade.withParticipants(
                registry.withEconomicState(home, registry.economicState(home).invalidate(
                    Map.of(SettlementEconomicPlanningState.Change.CASH, Set.of()))))));
        assertEquals(List.of(GoodsParticipantProcess.review(home, 4002)), SettlementEconomicReviewRequests.changed(planned, hard, 4001));
    }

}

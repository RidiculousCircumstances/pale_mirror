package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class GoodsParticipantReviewAdviceTest {
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

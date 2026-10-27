package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class GoodsParticipantReviewAdviceTest {
    @Test void capturedForecastRejectsChangedOwnerInputsPeerTermsAndExpiryButNotUnrelatedMotion() {
        var state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:advice-proof"), 126L,
                FrontierRulesets.installed("frontier-v3-simulation-baseline-r31")));
        var home = state.bootstrap().settlements().get(6).id();
        var action = GoodsParticipantProcess.review(home, 4000); var at = new SimInstant(4000);
        var completion = GoodsParticipantReviewAdvice.capture(state, action, at).prepare();
        assertTrue(completion.revalidate(state, new SimInstant(4001)).isPresent());
        var resident = state.humanPopulation().residents().values().iterator().next();
        var body = state.actorLocations().get(resident.id()).body();
        assertTrue(completion.revalidate(state.withActorBody(resident.id(), new BodyPosition(body.x() + 1, body.y(), body.z())),
                new SimInstant(4001)).isPresent(), "conditional economic advice is not a body-position or route authority");
        var trade = state.companies().goodsTrade(); var registry = trade.participants();
        var invalidated = registry.withEconomicState(home, registry.economicState(home).invalidate(
                Map.of(SettlementEconomicPlanningState.Change.CASH, Set.of())));
        assertTrue(completion.revalidate(state.withCompanies(state.companies().withGoodsTrade(trade.withParticipants(invalidated))), at).isEmpty());
        var owner = registry.participants().get(home); var peer = registry.participants().get(owner.known().getFirst().party().id());
        var peerChanged = registry.withEconomicState(peer.party().id(), registry.economicState(peer.party().id()).invalidate(
                Map.of(SettlementEconomicPlanningState.Change.STOCK, Set.of("minecraft:bread"))));
        assertTrue(completion.revalidate(state.withCompanies(state.companies().withGoodsTrade(trade.withParticipants(peerChanged))), at).isEmpty());
        assertTrue(completion.revalidate(state, new SimInstant(4000 + state.bootstrap().ruleset().goodsTrade().reviewInterval())).isEmpty());
        assertTrue(completion.revalidate(state, new SimInstant(3999)).isEmpty());
    }
}

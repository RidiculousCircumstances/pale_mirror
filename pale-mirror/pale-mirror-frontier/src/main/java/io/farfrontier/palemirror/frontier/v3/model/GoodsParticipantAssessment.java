package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.model.economy.EconomicPlanner;
import java.util.*;

/** Reads accepted decisions and current local stock/cash. No planner or fact collector. */
public final class GoodsParticipantAssessment {
    public record Assessment(long atTick, GoodsParticipantView view, Optional<SettlementSupplyFacts> facts,
                             Optional<EconomicPlanner.Domestic> domestic) {
        public Assessment {
            Objects.requireNonNull(view); Objects.requireNonNull(facts); Objects.requireNonNull(domestic);
            if (atTick < 0 || facts.filter(value -> value.atTick() != atTick).isPresent())
                throw new IllegalArgumentException("economic assessment has a foreign instant");
        }
    }
    private GoodsParticipantAssessment() { }
    public static GoodsParticipantView view(FrontierWorldState state, GoodsParticipant participant, long atTick) {
        return read(state, participant, atTick).view();
    }
    public static GoodsParticipantView trading(FrontierWorldState state, GoodsParticipant participant, long atTick) {
        return domestic(state, participant, atTick).view();
    }
    public static Assessment read(FrontierWorldState state, GoodsParticipant participant, long atTick) {
        return published(state, participant, atTick, true);
    }
    public static Assessment domestic(FrontierWorldState state, GoodsParticipant participant, long atTick) {
        return published(state, participant, atTick, false);
    }
    private static Assessment published(FrontierWorldState state, GoodsParticipant participant, long atTick, boolean exports) {
        if (atTick < 0) throw new IllegalArgumentException("negative economic assessment instant");
        var economic = state.companies().goodsTrade().participants().economicState(participant.party().id());
        var accepted = economic.accepted();
        var plan = accepted.map(value -> exports ? value.complete() : value.domestic());
        var current = GoodsParticipantView.planned(state, GoodsParticipantView.observe(state, participant), plan);
        var view = new GoodsParticipantView(current.participant(), current.residents(), current.availableMoney(), current.stocks(),
                current.purchaseLimits(), current.supplyPlan(), state.bootstrap().ruleset().goodsTrade().supplyPlanning().enabled()
                        && participant.policy() == GoodsPolicyKind.PUBLIC_SETTLEMENT
                        ? economic.awaitingTrade(atTick, current.stocks().keySet()) : Set.of(), current.receivableExposure());
        return new Assessment(accepted.map(SettlementEconomicPlan::atTick).orElse(atTick), view, Optional.empty(), Optional.empty());
    }
}

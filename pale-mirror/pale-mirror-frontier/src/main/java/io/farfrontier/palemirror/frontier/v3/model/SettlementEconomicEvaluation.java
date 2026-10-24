package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.model.economy.EconomicPlanner;
import io.farfrontier.palemirror.frontier.v3.model.supply.SupplyPlanner;
import java.util.*;

/** Explicit owner-invoked calculation. Never called by stock/eligibility/diagnostic reads. */
public final class SettlementEconomicEvaluation {
    private static final EconomicPlanner ECONOMY = new EconomicPlanner();
    private SettlementEconomicEvaluation() { }
    public static GoodsParticipantAssessment.Assessment domestic(FrontierWorldState state, GoodsParticipant participant, long atTick) {
        var observed = GoodsParticipantView.observe(state, participant);
        if (participant.policy() != GoodsPolicyKind.PUBLIC_SETTLEMENT
                || !state.bootstrap().ruleset().goodsTrade().supplyPlanning().enabled())
            return new GoodsParticipantAssessment.Assessment(atTick, GoodsParticipantView.planned(state, observed, Optional.empty()), Optional.empty(), Optional.empty());
        var facts = SettlementSupplyPlanning.collect(state, observed, atTick);
        var economicRules = state.bootstrap().ruleset().goodsTrade().supplyPlanning().economics();
        Optional<EconomicPlanner.Domestic> domestic = economicRules.map(rules ->
                ECONOMY.domestic(SettlementEconomicPlanning.facts(state, facts), rules));
        var plan = domestic.map(EconomicPlanner.Domestic::plan).orElseGet(() -> {
            var supply = new SupplyPlanner().plan(facts.stocks(), facts.capacities(), facts.commitments(), facts.market());
            return new SupplyPlanner.Plan(supply.demands(), facts.reserves(), supply.preferredPurchaseSpending(),
                    supply.preferredPurchaseQuantities(), supply.inputRequirements(), supply.economy(), supply.backedRecipes());
        });
        return new GoodsParticipantAssessment.Assessment(atTick, GoodsParticipantView.planned(state, observed, Optional.of(plan)), Optional.of(facts), domestic);
    }
    public static GoodsParticipantAssessment.Assessment complete(FrontierWorldState state, GoodsParticipantAssessment.Assessment domestic) {
        if (domestic.domestic().isEmpty()) return domestic;
        var plan = ECONOMY.withExports(domestic.domestic().orElseThrow(),
                SettlementEconomicPlanning.exports(state, domestic.facts().orElseThrow(), false,
                        ECONOMY.potentialExports(domestic.domestic().orElseThrow())));
        return new GoodsParticipantAssessment.Assessment(domestic.atTick(), GoodsParticipantView.planned(state,
                GoodsParticipantView.observe(state, domestic.view().participant()), Optional.of(plan)), domestic.facts(), domestic.domestic());
    }
}

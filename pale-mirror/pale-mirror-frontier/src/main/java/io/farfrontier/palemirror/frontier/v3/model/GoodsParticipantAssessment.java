package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.model.economy.EconomicPlanner;
import io.farfrontier.palemirror.frontier.v3.model.supply.SupplyPlanner;
import java.util.*;

/** Explicit economic evaluation. Observation stays cheap; domestic funding never
 * requests another participant's full plan. No result here grants execution authority. */
public final class GoodsParticipantAssessment {
    public record Assessment(long atTick, GoodsParticipantView view, Optional<SettlementSupplyFacts> facts,
                             Optional<EconomicPlanner.Domestic> domestic) {
        public Assessment {
            Objects.requireNonNull(view); Objects.requireNonNull(facts); Objects.requireNonNull(domestic);
            if (atTick < 0 || facts.filter(value -> value.atTick() != atTick).isPresent())
                throw new IllegalArgumentException("economic assessment has a foreign instant");
        }
    }
    private record Key(GoodsParticipant participant, long atTick, boolean exports) { }
    private static final ImmutableInputView<Map<Key, Assessment>> CACHE = new ImmutableInputView<>();
    private static final ImmutableResultView<Key, Assessment> COMPLETED = new ImmutableResultView<>(64);
    private static final EconomicPlanner ECONOMY = new EconomicPlanner();
    private GoodsParticipantAssessment() { }

    public static GoodsParticipantView view(FrontierWorldState state, GoodsParticipant participant, long atTick) {
        return read(state, participant, atTick).view();
    }
    /** A single production query need not evaluate unrelated export markets.
     * Any live downstream buy envelope retains the full shared export allocation. */
    public static long production(FrontierWorldState state, GoodsParticipant participant, String item, long atTick) {
        var domestic = domestic(state, participant, atTick);
        var evaluated = domestic.domestic().orElseThrow();
        var related = io.farfrontier.palemirror.frontier.v3.model.supply.ProductionReachability
                .consumers(evaluated.facts().processes(), item);
        boolean possible = state.companies().goodsTrade().orders().values().stream().anyMatch(order ->
                order.side() == GoodsTradeOrder.Side.BUY && order.counterparty().equals(participant.party())
                        && order.expiresAtTick() > atTick && order.availableQuantity() > 0 && related.contains(order.itemKind()));
        var plan = possible ? read(state, participant, atTick).view().supplyPlan().orElseThrow() : evaluated.plan();
        return Objects.requireNonNull(plan.economy().orElseThrow().additionalProduction().get(item), "undeclared production item");
    }
    /** Present stock, domestic requirements and funded consent. Trading existing
     * goods must not recursively plan production for prospective buyers.
     * Export production remains an explicit read/view assessment. */
    public static GoodsParticipantView trading(FrontierWorldState state, GoodsParticipant participant, long atTick) {
        return domestic(state, participant, atTick).view();
    }
    public static Assessment read(FrontierWorldState state, GoodsParticipant participant, long atTick) {
        return cached(state, participant, atTick, true);
    }
    public static Assessment domestic(FrontierWorldState state, GoodsParticipant participant, long atTick) {
        return cached(state, participant, atTick, false);
    }
    private static Assessment cached(FrontierWorldState state, GoodsParticipant participant,
            long atTick, boolean exports) {
        if (atTick < 0) throw new IllegalArgumentException("negative economic assessment instant");
        var cache = CACHE.get(List.of(state), HashMap::new);
        var key = new Key(participant, atTick, exports);
        var existing = cache.get(key);
        if (existing != null) return existing;
        // Explicit put: evaluation may populate other domestic entries in the same
        // cache; HashMap.computeIfAbsent forbids those nested structural changes.
        var result = COMPLETED.get(List.of(state), key, () -> exports
                ? complete(state, domestic(state, participant, atTick)) : evaluateDomestic(state, participant, atTick));
        if (cache.size() >= 256) cache.clear();
        cache.put(key, result);
        return result;
    }
    private static Assessment evaluateDomestic(FrontierWorldState state, GoodsParticipant participant, long atTick) {
        var observed = GoodsParticipantView.observe(state, participant);
        if (participant.policy() != GoodsPolicyKind.PUBLIC_SETTLEMENT
                || !state.bootstrap().ruleset().goodsTrade().supplyPlanning().enabled())
            return new Assessment(atTick, GoodsParticipantView.planned(state, observed, Optional.empty()), Optional.empty(), Optional.empty());
        var facts = SettlementSupplyPlanning.collect(state, observed, atTick);
        var economicRules = state.bootstrap().ruleset().goodsTrade().supplyPlanning().economics();
        Optional<EconomicPlanner.Domestic> domestic = economicRules.map(rules ->
                ECONOMY.domestic(SettlementEconomicPlanning.facts(state, facts), rules));
        var plan = domestic.map(EconomicPlanner.Domestic::plan).orElseGet(() -> {
            var supply = new SupplyPlanner().plan(facts.stocks(), facts.capacities(), facts.commitments(), facts.market());
            return new SupplyPlanner.Plan(supply.demands(), facts.reserves(), supply.preferredPurchaseSpending(),
                    supply.preferredPurchaseQuantities(), supply.inputRequirements(), supply.economy(), supply.backedRecipes());
        });
        return new Assessment(atTick, GoodsParticipantView.planned(state, observed, Optional.of(plan)), Optional.of(facts), domestic);
    }
    private static Assessment complete(FrontierWorldState state, Assessment domestic) {
        if (domestic.domestic().isEmpty()) return domestic;
        var plan = ECONOMY.withExports(domestic.domestic().orElseThrow(),
                SettlementEconomicPlanning.exports(state, domestic.facts().orElseThrow(), false,
                        ECONOMY.potentialExports(domestic.domestic().orElseThrow())));
        return new Assessment(domestic.atTick(), GoodsParticipantView.planned(state,
                GoodsParticipantView.observe(state, domestic.view().participant()), Optional.of(plan)), domestic.facts(), domestic.domestic());
    }
}

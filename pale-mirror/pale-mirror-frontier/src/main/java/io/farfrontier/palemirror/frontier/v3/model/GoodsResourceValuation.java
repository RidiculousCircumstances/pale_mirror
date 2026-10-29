package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.supply.SourcingValuation;
import java.util.*;

/** Replacement advice from declared local capabilities. No recursive market plan,
 * production authorization or claim that the resources are already available. */
public final class GoodsResourceValuation {
    private static final ImmutableKeyedInputView<GoodsPolicyKind, Map<String, Long>> REFERENCES = new ImmutableKeyedInputView<>(GoodsPolicyKind.values().length);
    private static final ImmutableKeyedInputView<GoodsParticipantScope, Map<String, FixedScalar>> VALUES = new ImmutableKeyedInputView<>(GoodsParticipantState.MAX_PARTICIPANTS);
    // A cheap exact-snapshot reread shares the completed value across commodities
    // and threads. New snapshots still enter the SAME owner-fact publication path.
    private static final ImmutableSnapshotView<GoodsParticipantScope, Map<String, FixedScalar>> READS =
            new ImmutableSnapshotView<>(2, GoodsParticipantState.MAX_PARTICIPANTS);
    private GoodsResourceValuation() { }

    public static FixedScalar replacement(FrontierWorldState state, GoodsParticipant participant, String item, long atTick) {
        return Objects.requireNonNull(values(state, participant, atTick).get(item), "undeclared valued resource");
    }
    /** Owner facts invalidate the underlying value; the exact-snapshot shortcut
     * only avoids repeated reads. Fresh stock/funding/consent admission stays separate. */
    static Map<String, FixedScalar> values(FrontierWorldState state, GoodsParticipant participant, long atTick) {
        if (atTick < 0) throw new IllegalArgumentException("negative valuation instant");
        return READS.get(state, GoodsParticipantScope.of(participant), () -> published(state, participant));
    }
    private static Map<String, FixedScalar> published(FrontierWorldState state, GoodsParticipant participant) {
        var prices = referencePrices(state, participant);
        var capacities = capacities(state, participant);
        return VALUES.getPublished(state.bootstrap(), GoodsParticipantScope.of(participant), List.of(prices, capacities),
                () -> assess(state, prices, capacities));
    }
    private static Map<String, Long> referencePrices(FrontierWorldState state, GoodsParticipant participant) {
        var rules = state.bootstrap().ruleset().goodsTrade();
        var history = state.companies().goodsTrade().priceHistory().references(rules.pricing().recencyTicks());
        return REFERENCES.getPublished(state.bootstrap(), participant.policy(), List.of(history), () -> {
            var prices = new TreeMap<String, Long>();
            rules.policies().get(participant.policy()).forEach(commodity -> prices.put(commodity.itemKind(),
                    history.getOrDefault(commodity.itemKind(), commodity.minimumSellPrice()).raw()));
            return Map.copyOf(prices);
        });
    }
    private static List<io.farfrontier.palemirror.frontier.v3.model.supply.ProductionCost> capacities(FrontierWorldState state, GoodsParticipant participant) {
        return state.bootstrap().ruleset().goodsTrade().supplyPlanning().sourcing().isPresent()
                ? SupplyCapacityComposition.forValuation(state, participant) : List.of();
    }
    private static Map<String, FixedScalar> assess(FrontierWorldState state, GoodsParticipant participant) {
        return assess(state, referencePrices(state, participant), capacities(state, participant));
    }
    private static Map<String, FixedScalar> assess(FrontierWorldState state, Map<String, Long> references,
            List<io.farfrontier.palemirror.frontier.v3.model.supply.ProductionCost> capacities) {
        var rules = state.bootstrap().ruleset().goodsTrade();
        var prices = new TreeMap<String, Long>(references);
        if (rules.supplyPlanning().sourcing().isPresent()) {
            var costs = SourcingValuation.costs(capacities, prices, List.of(),
                    rules.supplyPlanning().sourcing().orElseThrow().labourValuePerTick());
            // Replacement by a current local capability does not cost a past
            // emergency buyer's willingness to pay. Inputs without a local
            // capability retain an explicit external price estimate.
            var local = new TreeMap<String, Long>();
            capacities.forEach(process -> local.merge(process.product(), costs.get(process.id()), Math::min));
            prices.putAll(local); // A cheap historical receipt is not an executable replacement either.
        }
        var result = new TreeMap<String, FixedScalar>();
        prices.forEach((item, price) -> result.put(item, new FixedScalar(price)));
        return Map.copyOf(result);
    }
}

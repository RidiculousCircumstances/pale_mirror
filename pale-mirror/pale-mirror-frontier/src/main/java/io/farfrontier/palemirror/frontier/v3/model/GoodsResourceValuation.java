package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.supply.SourcingValuation;
import java.util.*;

/** Replacement advice from declared local capabilities. No recursive market plan,
 * production authorization or claim that the resources are already available. */
public final class GoodsResourceValuation {
    private record Query(GoodsParticipant participant, long atTick) { }
    private static final ImmutableInputView<BoundedValueMemo<Query, Map<String, FixedScalar>>> VALUES = new ImmutableInputView<>();
    private GoodsResourceValuation() { }

    public static FixedScalar replacement(FrontierWorldState state, GoodsParticipant participant, String item, long atTick) {
        if (atTick < 0) throw new IllegalArgumentException("negative valuation instant");
        return Objects.requireNonNull(VALUES.get(List.of(state), () -> new BoundedValueMemo<Query, Map<String, FixedScalar>>(256))
                .get(new Query(participant, atTick), () -> assess(state, participant, atTick)).get(item), "undeclared valued resource");
    }
    private static Map<String, FixedScalar> assess(FrontierWorldState state, GoodsParticipant participant, long atTick) {
        var rules = state.bootstrap().ruleset().goodsTrade();
        var prices = new TreeMap<String, Long>();
        rules.policies().get(participant.policy()).forEach(commodity -> prices.put(commodity.itemKind(),
                state.companies().goodsTrade().priceHistory().reference(commodity.itemKind(), rules.pricing().recencyTicks())
                        .orElse(commodity.minimumSellPrice()).raw()));
        if (rules.supplyPlanning().sourcing().isPresent()) {
            var capacities = SupplyCapacityComposition.forValuation(state, participant);
            var costs = SourcingValuation.processes(capacities, prices, List.of(),
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

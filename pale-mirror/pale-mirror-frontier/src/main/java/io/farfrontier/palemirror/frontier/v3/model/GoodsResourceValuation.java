package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.supply.SourcingValuation;
import java.util.*;

/** Replacement advice from declared local capabilities. No recursive market plan,
 * production authorization or claim that the resources are already available. */
public final class GoodsResourceValuation {
    private static final ImmutableInputView<ImmutableResultView<io.farfrontier.palemirror.frontier.v3.api.SubjectId,
            Map<String, FixedScalar>>> VALUES = new ImmutableInputView<>();
    private GoodsResourceValuation() { }

    public static FixedScalar replacement(FrontierWorldState state, GoodsParticipant participant, String item, long atTick) {
        if (atTick < 0) throw new IllegalArgumentException("negative valuation instant");
        // Installed capability and retained price history belong to the exact
        // snapshot. Passage of query time alone changes neither input; dated
        // provisions, consent and deadlines remain the calling owners' facts.
        return Objects.requireNonNull(VALUES.get(List.of(state), () -> new ImmutableResultView<>(256))
                .get(List.of(participant), participant.party().id(), () -> assess(state, participant)).get(item), "undeclared valued resource");
    }
    private static Map<String, FixedScalar> assess(FrontierWorldState state, GoodsParticipant participant) {
        var rules = state.bootstrap().ruleset().goodsTrade();
        var prices = new TreeMap<String, Long>();
        rules.policies().get(participant.policy()).forEach(commodity -> prices.put(commodity.itemKind(),
                state.companies().goodsTrade().priceHistory().reference(commodity.itemKind(), rules.pricing().recencyTicks())
                        .orElse(commodity.minimumSellPrice()).raw()));
        if (rules.supplyPlanning().sourcing().isPresent()) {
            var capacities = SupplyCapacityComposition.forValuation(state, participant);
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

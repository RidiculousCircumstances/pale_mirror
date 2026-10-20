package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.supply.SupplyPriority;
import java.util.*;

/** Supply-owned projection: consumers receive priorities, never recipe-specific business rules. */
public final class ProductionResourcePriorities {
    private ProductionResourcePriorities() { }
    public static Map<String, SupplyPriority> read(FrontierWorldState state, SubjectId home,
            Set<String> products, Optional<SubjectId> completing, long atTick) {
        var rules = state.bootstrap().ruleset().goodsTrade().supplyPlanning();
        var participant = Objects.requireNonNull(state.companies().goodsTrade().participants().participants().get(home));
        var plan = rules.enabled() ? GoodsParticipantAssessment.view(state, participant, atTick).supplyPlan() : Optional
                .<io.farfrontier.palemirror.frontier.v3.model.supply.SupplyPlanner.Plan>empty();
        var result = new LinkedHashMap<String, SupplyPriority>();
        for (var product : products.stream().sorted().toList()) {
            var demand = ProductionStockDemand.read(state, home, product, completing, atTick);
            result.put(product, SupplyPriority.from(demand.missing(), demand.target(),
                    plan.map(value -> value.require(product)), rules.enabled() ? rules.require(product).leadTicks() : 0));
        }
        return Map.copyOf(result);
    }
}

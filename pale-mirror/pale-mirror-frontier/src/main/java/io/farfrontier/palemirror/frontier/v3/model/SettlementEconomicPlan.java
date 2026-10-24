package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.model.supply.SupplyPlanner;
import java.util.*;

/** Accepted intentions, not stock, cash or execution authority. Production ceilings
 * use the stock owner's coverage at acceptance, never a new baseline on each read.
 * Buyer version zero explicitly records that no plan has yet been published. */
public record SettlementEconomicPlan(long revision, long inputRevision, long atTick, long reviewAtTick,
        SupplyPlanner.Plan domestic, SupplyPlanner.Plan complete, Map<String, Long> productionCeilings,
        Map<io.farfrontier.palemirror.frontier.v3.api.SubjectId, Long> buyerVersions,
        Map<String, Set<String>> affectedProducts, Optional<CultivationEconomicIntent> cultivation,
        Map<String, io.farfrontier.palemirror.frontier.v3.model.supply.SupplyReservePlanner.Need> needs) {
    public SettlementEconomicPlan {
        cultivation = Objects.requireNonNull(cultivation); needs = Map.copyOf(needs);
        Objects.requireNonNull(domestic); Objects.requireNonNull(complete);
        productionCeilings = Map.copyOf(productionCeilings); buyerVersions = Map.copyOf(buyerVersions);
        affectedProducts = affectedProducts.entrySet().stream().collect(java.util.stream.Collectors.toUnmodifiableMap(
                Map.Entry::getKey, entry -> Set.copyOf(entry.getValue())));
        if (revision < 1 || inputRevision < 0 || atTick < 0 || reviewAtTick <= atTick
                || !domestic.demands().keySet().equals(complete.demands().keySet())
                || !needs.keySet().equals(complete.demands().keySet())
                || !productionCeilings.keySet().equals(complete.demands().keySet())
                || !affectedProducts.keySet().equals(complete.demands().keySet())
                || affectedProducts.values().stream().anyMatch(items -> !complete.demands().keySet().containsAll(items))
                || productionCeilings.values().stream().anyMatch(value -> value < 0)
                || buyerVersions.size() > GoodsParticipantState.MAX_PARTICIPANTS
                || buyerVersions.values().stream().anyMatch(value -> value < 0))
            throw new IllegalArgumentException("invalid accepted economic plan");
    }
}

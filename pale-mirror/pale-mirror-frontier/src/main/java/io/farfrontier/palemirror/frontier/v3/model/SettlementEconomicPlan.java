package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.model.supply.SupplyPlanner;
import java.util.*;

/** Accepted intentions, not stock, cash or execution authority. Production ceilings
 * use the stock owner's coverage at acceptance, never a new baseline on each read.
 * Peer versions fence actual conditional offers, not every observed alternative.
 * An explicit zero entry records an absent publication, never zero demand. */
public record SettlementEconomicPlan(long revision, long inputRevision, long atTick, long reviewAtTick,
        SupplyPlanner.Plan domestic, SupplyPlanner.Plan complete, Map<String, Long> productionCeilings,
        Map<io.farfrontier.palemirror.frontier.v3.api.SubjectId, Long> peerVersions,
        Map<String, Set<String>> affectedProducts, Optional<CultivationEconomicIntent> cultivation,
        Map<String, io.farfrontier.palemirror.frontier.v3.model.supply.SupplyReservePlanner.Need> needs,
        List<ConditionalProductionQuote> conditionalQuotes) {
    public static final int MAX_CONDITIONAL_QUOTES = 4096;
    public SettlementEconomicPlan {
        cultivation = Objects.requireNonNull(cultivation); needs = Map.copyOf(needs); conditionalQuotes = List.copyOf(conditionalQuotes);
        Objects.requireNonNull(domestic); Objects.requireNonNull(complete);
        productionCeilings = Map.copyOf(productionCeilings); peerVersions = Map.copyOf(peerVersions);
        affectedProducts = affectedProducts.entrySet().stream().collect(java.util.stream.Collectors.toUnmodifiableMap(
                Map.Entry::getKey, entry -> Set.copyOf(entry.getValue())));
        var declaredPeers = peerVersions;
        if (revision < 1 || inputRevision < 0 || atTick < 0 || reviewAtTick <= atTick
                || !domestic.demands().keySet().equals(complete.demands().keySet())
                || !needs.keySet().equals(complete.demands().keySet())
                || !productionCeilings.keySet().equals(complete.demands().keySet())
                || !affectedProducts.keySet().equals(complete.demands().keySet())
                || affectedProducts.values().stream().anyMatch(items -> !complete.demands().keySet().containsAll(items))
                || productionCeilings.values().stream().anyMatch(value -> value < 0)
                || peerVersions.size() > GoodsParticipantState.MAX_PARTICIPANTS
                || peerVersions.values().stream().anyMatch(value -> value < 0)
                || conditionalQuotes.size() > MAX_CONDITIONAL_QUOTES
                || conditionalQuotes.stream().map(ConditionalProductionQuote::key).distinct().count() != conditionalQuotes.size()
                || conditionalQuotes.stream().anyMatch(quote -> quote.sourcePlanRevision() != revision
                    || quote.sourceInputRevision() != inputRevision || quote.assessedAtTick() != atTick
                    || !complete.demands().containsKey(quote.itemKind())
                    || declaredPeers.getOrDefault(quote.recipient().id(), 0L) < 1))
            throw new IllegalArgumentException("invalid accepted economic plan");
    }
    /** Rebind only the publication generation after the owner proves that intervening
     * inputs were dated market notifications. Assessment/expiry and all terms stay dated. */
    public SettlementEconomicPlan withInputRevision(long next) {
        if (next < inputRevision) throw new IllegalArgumentException("economic input generation cannot move backwards");
        if (next == inputRevision) return this;
        var quotes = conditionalQuotes.stream().map(q -> new ConditionalProductionQuote(q.supplier(), q.recipient(), q.itemKind(),
                q.quantity(), q.goodsUnitPrice(), q.resourceFreight(), q.cashFreight(), q.fulfillment(), q.capacityWindowTicks(),
                q.deliveryTicks(), next, q.sourcePlanRevision(), q.assessedAtTick(), q.expiresAtTick())).toList();
        return new SettlementEconomicPlan(revision, next, atTick, reviewAtTick, domestic, complete, productionCeilings,
                peerVersions, affectedProducts, cultivation, needs, quotes);
    }

}

package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import io.farfrontier.palemirror.frontier.v3.model.market.*;
import java.util.*;

/** Economic advice for recurring public production. Saleable quantity remains
 * reserve-owned; recipes supply value, matching alone grants a funded sale. */
final class GoodsEconomicPricing {
    static ResourceEconomicValue assess(FrontierWorldState state, GoodsParticipantView view, String item,
            Map<String, FixedScalar> references, GoodsProductionPricing.Assessment production,
            Optional<AcquisitionQuote> acquisition, long now) {
        var rules = state.bootstrap().ruleset().goodsTrade();
        var prices = new TreeMap<String, Long>(); references.forEach((key, value) -> prices.put(key, value.raw()));
        // Retaining a reproducible resource is worth the cheaper real option:
        // make it or replace it from current available external stock. Historical
        // cheap receipts are not replacement offers. Selling below one's own
        // production cost does not authorize new production at that price.
        var retained = production.opportunities().getOrDefault(item, FixedScalar.ZERO);
        if (retained.raw() > 0) {
            var replacement = GoodsAcquisitionPricing.estimate(state, view.participant(), item,
                    Math.max(1, view.stocks().get(item).unclaimedInStorage()), now);
            if (replacement.isPresent()) retained = new FixedScalar(Math.min(retained.raw(), replacement.orElseThrow().total().raw()));
        }
        // The reserve owner has already excluded necessary stock from sale.
        // Under a forecast cash deficit, selling that optional surplus is not
        // new production: sunk manufacturing cost cannot veto positive proceeds.
        // Production admission keeps its independent profitability assessment.
        var economic = view.supplyPlan().orElseThrow().economy().orElseThrow();
        if (economic.unfundedRequirements() > 0 && economic.saleableStock().getOrDefault(item, 0L) > 0)
            retained = FixedScalar.ZERO;
        var outputValues = new TreeMap<>(prices);
        production.opportunities().forEach((key, value) -> outputValues.merge(key, value.raw(), Math::max));
        long inputValue = ProductionInputValue.assess(item, production.processes(), outputValues,
                rules.supplyPlanning().sourcing().orElseThrow().labourValuePerTick());
        long value = Math.max(references.get(item).raw(), inputValue);
        // The supply owner already selected a net requirement. An available
        // replacement must not be rejected solely because past trades were cheap.
        if (acquisition.isPresent()) value = Math.max(value, acquisition.orElseThrow().total().raw());
        var local = production.alternatives().get(item);
        if (local != null) value = Math.min(value, local.raw());
        long quantity = view.supplyPlan().orElseThrow().require(item).purchase();
        if (quantity > 0) value = Math.min(value, Math.max(1, GoodsParticipantFunding.purchase(view, rules, item).raw()));
        return new ResourceEconomicValue(retained, new FixedScalar(Math.max(1, value)));
    }
    private GoodsEconomicPricing() { }
}

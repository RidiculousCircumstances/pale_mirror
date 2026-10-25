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
        // A cash shortfall does not erase the future use/replacement value of stock.
        // Saleable quantity and the value of retaining it are separate decisions.
        var outputValues = new TreeMap<>(prices);
        // A costly way of making an output does not increase the value of its
        // inputs. Only independently observed output value supports substitution.
        long inputValue = ProductionInputValue.assess(item, production.processes(), outputValues,
                rules.supplyPlanning().sourcing().orElseThrow().labourValuePerTick());
        long value = Math.max(references.get(item).raw(), inputValue);
        // A published production counterquote is price advice, never ready
        // supply. It closes negotiation before sell stock exists. Keep it out
        // of retained-stock value and physical/dated supply forecasts.
        long quantity = view.supplyPlan().orElseThrow().require(item).purchase();
        long cash = GoodsParticipantFunding.purchase(view, rules, item).raw();
        var conditional = acquisition.isEmpty() ? ConditionalSupplyPricing.best(state, view.participant(), item,
                quantity, cash, now) : Optional.<ConditionalSupplyPricing.Estimate>empty();
        if (conditional.isPresent()) value = conditional.orElseThrow().resourceUnitExpense();
        // The cheapest current replacement bounds consent; expensive history or
        // a worse local process cannot justify outbidding that alternative.
        if (acquisition.isPresent()) value = acquisition.orElseThrow().total().raw();
        var local = production.alternatives().get(item);
        if (local != null) value = Math.min(value, local.raw());
        // Qualified quotes already bound invoiced cash. Their resource expense
        // can also include provisions already owned by the collector; it is not
        // another cash invoice. Matching still rechecks actual money and goods.
        if (quantity > 0 && acquisition.isEmpty() && conditional.isEmpty()) value = Math.min(value, Math.max(1, cash));
        return new ResourceEconomicValue(retained, new FixedScalar(Math.max(1, value)));
    }
    private GoodsEconomicPricing() { }
}

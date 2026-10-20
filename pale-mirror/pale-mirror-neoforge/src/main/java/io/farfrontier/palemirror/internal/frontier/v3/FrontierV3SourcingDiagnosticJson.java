package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.SettlementSupplyFacts;
import java.util.Comparator;
import java.util.stream.Collectors;

/** Bounded actual inputs of the reported decision, never another planning invocation. */
final class FrontierV3SourcingDiagnosticJson {
    static String render(SettlementSupplyFacts facts) {
        var market = facts.market();
        var offers = market.offers().stream().sorted(Comparator.comparing(
                io.farfrontier.palemirror.frontier.v3.model.supply.SourcingMarket.Offer::id)).limit(32)
                .map(offer -> "{\"id\":" + text(offer.id()) + ",\"item\":" + text(offer.item())
                        + ",\"quantity\":" + offer.quantity() + ",\"goodsCashPerItemRaw\":" + offer.cashPerItem()
                        + ",\"deliveredCostPerItemRaw\":" + offer.deliveredCostPerItem()
                        + ",\"tripCashRaw\":" + offer.tripCashCost() + ",\"tripComparativeCostRaw\":" + offer.tripCost()
                        + ",\"deliveryTicks\":" + offer.deliveryTicks() + "}")
                .collect(Collectors.joining(",", "[", "]"));
        var processes = facts.capacities().stream().sorted(Comparator.comparing(
                io.farfrontier.palemirror.frontier.v3.model.supply.SupplyPlanner.Process::id)).limit(32)
                .map(process -> "{\"id\":" + text(process.id()) + ",\"product\":" + text(process.product())
                        + ",\"maximumBatches\":" + process.maximumBatches() + ",\"outputPerBatch\":" + process.outputPerBatch()
                        + ",\"comparativeUnitCostRaw\":" + market.processUnitCosts().get(process.id()) + "}")
                .collect(Collectors.joining(",", "[", "]"));
        return "{\"atTick\":" + facts.atTick() + ",\"availableCashRaw\":" + market.availableMoney()
                + ",\"offers\":" + offers + ",\"processes\":" + processes
                + ",\"omittedOffers\":" + Math.max(0, market.offers().size() - 32)
                + ",\"omittedProcesses\":" + Math.max(0, facts.capacities().size() - 32) + "}";
    }
    private static String text(String value) { return "\"" + FrontierV3DiagnosticJson.quote(value) + "\""; }
    private FrontierV3SourcingDiagnosticJson() { }
}

package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.economy.EconomicPlan;
import java.util.*;
import java.util.stream.Collectors;

/** Read-only economic intent. Conditional receipts never appear as available treasury. */
final class FrontierV3EconomicDiagnosticJson {
    static String render(EconomicPlan plan) {
        return "{\"horizonTicks\":" + plan.horizonTicks()
                + ",\"domesticTargets\":" + quantities(plan.domesticTargets())
                + ",\"productionTargets\":" + quantities(plan.productionTargets())
                + ",\"purchaseBudgetsRaw\":" + quantities(plan.purchaseBudgets())
                + ",\"retainedLiquidityRaw\":" + plan.retainedLiquidity()
                + ",\"unfundedRequirementsRaw\":" + plan.unfundedRequirements()
                + ",\"contractReceivablesRaw\":" + plan.contractReceivables()
                + ",\"constraints\":[" + plan.constraints().stream().sorted().map(c -> string(c.name())).collect(Collectors.joining(",")) + "]"
                + ",\"checkpoints\":[" + plan.checkpoints().stream().map(c -> "{\"afterTicks\":" + c.afterTicks()
                    + ",\"netRequirements\":" + quantities(c.netRequirements()) + ",\"importCostRaw\":" + c.importCost()
                    + ",\"cashWithoutNewSalesRaw\":" + c.cashWithoutNewSales() + "}").collect(Collectors.joining(",")) + "]"
                + ",\"exports\":[" + plan.exports().stream().map(e -> "{\"item\":" + string(e.item())
                    + ",\"existingStock\":" + e.existingStock() + ",\"additionalProduction\":" + e.additionalProduction()
                    + ",\"conditionalRevenueRaw\":" + e.conditionalRevenue() + "}").collect(Collectors.joining(",")) + "]}";
    }
    private static String quantities(Map<String, Long> values) {
        return "{" + values.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .map(e -> string(e.getKey()) + ":" + e.getValue()).collect(Collectors.joining(",")) + "}";
    }
    private static String string(String value) { return "\"" + FrontierV3DiagnosticJson.quote(value) + "\""; }
    private FrontierV3EconomicDiagnosticJson() { }
}

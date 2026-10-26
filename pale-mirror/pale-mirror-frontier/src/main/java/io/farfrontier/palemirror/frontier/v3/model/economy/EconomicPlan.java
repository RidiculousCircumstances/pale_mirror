package io.farfrontier.palemirror.frontier.v3.model.economy;

import java.util.*;

/** Derived intent. Funded restart items contribute to a complete quoted production bundle,
 * independently of unfunded sibling productions; they do not promise on-time inputs.
 * Additional production is net new output, not a target against another stock scope.
 * Only canonical admission owners may turn it into obligations. */
public record EconomicPlan(long horizonTicks, Map<String, Long> domesticTargets,
        Map<String, Long> additionalProduction, Map<String, Long> purchaseBudgets,
        long retainedLiquidity, long unfundedRequirements, long contractReceivables, List<Checkpoint> checkpoints,
        List<Export> exports, Set<Constraint> constraints, Map<String, Long> purchasePrices,
        Set<String> fundedRestarts, Map<String, Long> saleableStock, Map<String, Long> operatingBudgets) {
    public EconomicPlan(long horizonTicks, Map<String, Long> domesticTargets, Map<String, Long> additionalProduction, Map<String, Long> purchaseBudgets, long retainedLiquidity, long unfundedRequirements, long contractReceivables,
                List<Checkpoint> checkpoints, List<Export> exports, Set<Constraint> constraints, Map<String, Long> purchasePrices, Set<String> fundedRestarts, Map<String, Long> saleableStock) {
        this(horizonTicks, domesticTargets, additionalProduction, purchaseBudgets, retainedLiquidity, unfundedRequirements, contractReceivables, checkpoints, exports, constraints, purchasePrices, fundedRestarts, saleableStock, Map.of());
    }
    public long operatingCashRequirement() { return operatingBudgets.values().stream().reduce(0L, Math::addExact); }
    public enum Constraint { MATERIAL_SHORTFALL, CASH_SHORTFALL, CONDITIONAL_EXPORT_INCOME, NO_EXPORT_MARKET }
    public record Checkpoint(long afterTicks, Map<String, Long> netRequirements, long importCost, long cashWithoutNewSales) {
        public Checkpoint {
            netRequirements = Map.copyOf(netRequirements);
            if (afterTicks < 1 || importCost < 0 || netRequirements.values().stream().anyMatch(v -> v < 0))
                throw new IllegalArgumentException("invalid economic forecast checkpoint");
        }
    }
    public record Export(String item, long existingStock, long additionalProduction, long conditionalRevenue) {
        public Export {
            if (item == null || item.isBlank() || existingStock < 0 || additionalProduction < 0 || conditionalRevenue < 0)
                throw new IllegalArgumentException("invalid conditional export plan");
        }
    }
    public EconomicPlan {
        domesticTargets = Map.copyOf(domesticTargets); additionalProduction = Map.copyOf(additionalProduction);
        purchaseBudgets = Map.copyOf(purchaseBudgets); checkpoints = List.copyOf(checkpoints);
        exports = List.copyOf(exports); constraints = Set.copyOf(constraints);
        purchasePrices = Map.copyOf(purchasePrices); fundedRestarts = Set.copyOf(fundedRestarts);
        saleableStock = Map.copyOf(saleableStock); operatingBudgets = Map.copyOf(operatingBudgets);
        if (!domesticTargets.keySet().containsAll(operatingBudgets.keySet()) || operatingBudgets.values().stream().anyMatch(q -> q < 0)) throw new IllegalArgumentException("invalid operating envelope");
        if (horizonTicks < 1 || retainedLiquidity < 0 || unfundedRequirements < 0 || contractReceivables < 0
                || !domesticTargets.keySet().equals(additionalProduction.keySet())
                || !domesticTargets.keySet().containsAll(purchaseBudgets.keySet())
                || !domesticTargets.keySet().equals(purchasePrices.keySet())
                || !domesticTargets.keySet().equals(saleableStock.keySet())
                || !domesticTargets.keySet().containsAll(fundedRestarts)
                || domesticTargets.values().stream().anyMatch(v -> v < 0)
                || additionalProduction.values().stream().anyMatch(v -> v < 0)
                || purchaseBudgets.values().stream().anyMatch(v -> v < 0)
                || purchasePrices.values().stream().anyMatch(v -> v < 1)
                || saleableStock.values().stream().anyMatch(v -> v < 0))
            throw new IllegalArgumentException("invalid economic plan");
    }
}

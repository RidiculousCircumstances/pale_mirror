package io.farfrontier.palemirror.frontier.v3.model.economy;

import java.util.*;

/** Derived intent. Additional production is net new output, not a target against another stock scope.
 * Only canonical admission owners may turn it into obligations. */
public record EconomicPlan(long horizonTicks, Map<String, Long> domesticTargets,
        Map<String, Long> additionalProduction, Map<String, Long> purchaseBudgets,
        long retainedLiquidity, long unfundedRequirements, long contractReceivables, List<Checkpoint> checkpoints,
        List<Export> exports, Set<Constraint> constraints) {
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
        if (horizonTicks < 1 || retainedLiquidity < 0 || unfundedRequirements < 0 || contractReceivables < 0
                || !domesticTargets.keySet().equals(additionalProduction.keySet())
                || !domesticTargets.keySet().containsAll(purchaseBudgets.keySet())
                || domesticTargets.values().stream().anyMatch(v -> v < 0)
                || additionalProduction.values().stream().anyMatch(v -> v < 0)
                || purchaseBudgets.values().stream().anyMatch(v -> v < 0))
            throw new IllegalArgumentException("invalid economic plan");
    }
}

package io.farfrontier.palemirror.frontier.v3.model.supply;

/** Dates the first missing input from output consumption, not from completion
 * of the entire desired reserve. Quantities are recipes, not transport stacks. */
final class ProductionInputTiming {
    record Need(long deadline, long urgentRequired) { }

    static Need assess(SupplyPlanner.Stock product, SupplyPlanner.Stock input, SupplyPlanner.Process process,
                       long allocated, long recipes, long priorRequired, long deadline,
                       long unitsPerRecipe, long unitsPerItem, long availableProcessUnits) {
        long available = Math.max(0, input.covered() - priorRequired);
        long lo = 0, hi = recipes;
        while (lo < hi) {
            long mid = lo + (hi - lo + 1) / 2;
            if (required(mid, unitsPerRecipe, unitsPerItem, availableProcessUnits) <= available) lo = mid;
            else hi = mid - 1;
        }
        if (lo == recipes || deadline == Long.MAX_VALUE) return new Need(Long.MAX_VALUE, 0);
        long processing = lo == 0 && allocated == 0 ? process.firstOutputDelayTicks() : process.operationTicks();
        long coverage = product.consumptionUnits() == 0 ? 0 : Math.multiplyExact(
                Math.multiplyExact(lo, process.outputPerBatch()), product.consumptionPeriodTicks()) / product.consumptionUnits();
        long neededAt = Math.max(0, Math.addExact(deadline, coverage) - processing);
        long urgentRecipes = 0;
        if (neededAt <= input.policy().leadTicks()) {
            // Only enough output to bridge the lead window, or restart one
            // operation when downstream consumption has no continuous rate.
            long window = Math.max(0, Math.addExact(input.policy().leadTicks(), processing) - deadline);
            urgentRecipes = product.consumptionUnits() == 0 ? Math.addExact(lo, 1)
                    : Math.max(Math.addExact(lo, 1), Math.ceilDiv(Math.multiplyExact(window, product.consumptionUnits()),
                            Math.multiplyExact(product.consumptionPeriodTicks(), process.outputPerBatch())));
        }
        return new Need(neededAt, required(Math.min(recipes, urgentRecipes), unitsPerRecipe, unitsPerItem, availableProcessUnits));
    }

    private static long required(long recipes, long units, long capacity, long credit) {
        return Math.ceilDiv(Math.max(0, Math.multiplyExact(recipes, units) - credit), capacity);
    }
    private ProductionInputTiming() { }
}

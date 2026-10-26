package io.farfrontier.palemirror.frontier.v3.model;

/** Conservative finite fuel budget. No inventories, workers, recipes-by-name or runtime mutations. */
public final class ProductionFuelReserve {
    private ProductionFuelReserve() { }

    public static long requiredItems(ProductionCatalog catalog, WorkCatalog work, ProductionRecipe.Id recipeId,
            String fuelKind, int inputLimit, int concurrentStations, long horizonTicks) {
        if (inputLimit < 1 || inputLimit > 64 || concurrentStations < 1 || horizonTicks < 1)
            throw new IllegalArgumentException("fuel reserve requires bounded input, stations and positive horizon");
        var recipe = catalog.require(recipeId);
        if (recipe.nominalBurnTicksPerBatch(work) == 0) return 0;
        int batches = Math.min(inputLimit / recipe.inputUnitsPerBatch(), 64 / recipe.product().quantity());
        if (batches == 0) throw new IllegalArgumentException("input capacity cannot execute the declared recipe");
        long duration = catalog.requireFuel(fuelKind).burnTicks();
        // Even an arbitrarily fast recipe cannot ignite fuel faster than the
        // current item expires. Include one unknown partial interval at the boundary.
        return Math.multiplyExact(Math.addExact(Math.ceilDiv(horizonTicks, duration), 1L), concurrentStations);
    }
}

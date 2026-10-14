package io.farfrontier.palemirror.frontier.v3.model;

/** Conservative finite fuel budget. No inventories, workers, recipes-by-name or runtime mutations. */
public final class ProductionFuelReserve {
    private ProductionFuelReserve() { }

    public static long requiredItems(ProductionCatalog catalog, WorkCatalog work, ProductionRecipe.Id recipeId,
            String fuelKind, int inputLimit, int concurrentStations, long horizonTicks) {
        if (inputLimit < 1 || inputLimit > 64 || concurrentStations < 1 || horizonTicks < 1)
            throw new IllegalArgumentException("fuel reserve requires bounded input, stations and positive horizon");
        var recipe = catalog.require(recipeId);
        if (recipe.heatPerBatch() == 0) return 0;
        int batches = Math.min(inputLimit / recipe.ingredient().quantity(), 64 / recipe.product().quantity());
        if (batches == 0) throw new IllegalArgumentException("input capacity cannot execute the declared recipe");
        var definition = work.require(recipe.product().itemKind(), WorkOperation.PRODUCE);
        long minimumDuration = Math.ceilDiv(Math.multiplyExact(definition.workUnits(), 1_000L), work.maxSpeedPermille());
        // Include an operation already funded at the boundary; omit no energy
        // because a worker is eating, asleep, travelling or blocked.
        long operations = Math.addExact(Math.ceilDiv(horizonTicks, minimumDuration), 1L);
        long heat = Math.multiplyExact(Math.multiplyExact(operations, concurrentStations),
                Math.multiplyExact(batches, recipe.heatPerBatch()));
        return Math.ceilDiv(heat, catalog.requireFuel(fuelKind).heatUnits());
    }
}

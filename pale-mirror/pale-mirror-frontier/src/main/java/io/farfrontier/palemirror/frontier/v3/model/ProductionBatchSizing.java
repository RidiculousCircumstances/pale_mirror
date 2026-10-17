package io.farfrontier.palemirror.frontier.v3.model;

/** Recipe-neutral feasible portion. This calculates quantities, never claims stock or spends heat. */
public final class ProductionBatchSizing {
    /** Physical input/output transfer limits constrain one operation independently of the recipe. */
    public static int maximumRecipeBatches(ProductionRecipe recipe) {
        return Math.min(64 / recipe.ingredient().quantity(), 64 / recipe.product().quantity());
    }
    public static int inputQuantity(ProductionRecipe recipe, int availableInput, int outputCapacity, long availableHeat) {
        if (availableInput < 0 || outputCapacity < 0 || availableHeat < 0)
            throw new IllegalArgumentException("negative production batch capacity");
        long batches = Math.min(maximumRecipeBatches(recipe), Math.min(availableInput / recipe.ingredient().quantity(),
                outputCapacity / recipe.product().quantity()));
        if (recipe.heatPerBatch() > 0) batches = Math.min(batches, availableHeat / recipe.heatPerBatch());
        return Math.toIntExact(batches * recipe.ingredient().quantity());
    }
    private ProductionBatchSizing() { }
}

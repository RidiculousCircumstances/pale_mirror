package io.farfrontier.palemirror.frontier.v3.model;

/** Recipe-neutral feasible portion. This calculates quantities, never claims stock or spends heat. */
public final class ProductionBatchSizing {
    /** Physical input/output transfer limits constrain one operation independently of the recipe. */
    public static int maximumRecipeBatches(ProductionRecipe recipe) {
        if (!(recipe.outcome() instanceof ProductionRecipeOutcome.Fungible)) return 1;
        return Math.min(64 / recipe.inputUnitsPerBatch(), 64 / recipe.product().quantity());
    }
    public static int inputQuantity(ProductionRecipe recipe, int availableInput, int outputCapacity) {
        if (availableInput < 0 || outputCapacity < 0)
            throw new IllegalArgumentException("negative production batch capacity");
        long batches = Math.min(maximumRecipeBatches(recipe), Math.min(availableInput / recipe.inputUnitsPerBatch(),
                outputCapacity / recipe.product().quantity()));
        return Math.toIntExact(batches * recipe.inputUnitsPerBatch());
    }
    private ProductionBatchSizing() { }
}

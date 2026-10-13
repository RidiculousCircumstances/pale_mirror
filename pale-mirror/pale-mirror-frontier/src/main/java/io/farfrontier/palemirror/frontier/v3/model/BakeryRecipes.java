package io.farfrontier.palemirror.frontier.v3.model;

/** Bakery policy names its recipe explicitly; generic processing never discovers an owner. */
public final class BakeryRecipes {
    public static final ProductionRecipe.Id BREAD = new ProductionRecipe.Id("pale_mirror:bread");
    private BakeryRecipes() { }
    public static ProductionRecipe bread(FrontierWorldState state) {
        return state.bootstrap().ruleset().production().require(BREAD);
    }
}

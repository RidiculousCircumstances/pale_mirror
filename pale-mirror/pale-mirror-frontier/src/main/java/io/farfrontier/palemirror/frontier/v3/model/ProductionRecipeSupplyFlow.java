package io.farfrontier.palemirror.frontier.v3.model;
import io.farfrontier.palemirror.frontier.v3.model.supply.SupplyReservePlanner.Rate;
import java.util.*;
/** Recipe-owned material and combustion inputs per output unit, shared by attended and automatic reserve forecasts. */
final class ProductionRecipeSupplyFlow {
    static Map<String,Rate> read(FrontierRuleset rules,ProductionRecipe recipe){
        var inputs=new TreeMap<String,Rate>();
        ProductionRecipeInputs.select(recipe,kind->0L).forEach((kind,count)->inputs.merge(kind,Rate.of(count,recipe.product().quantity()),Rate::plus));
        long burn=recipe.nominalBurnTicksPerBatch(rules.workCatalog());
        if(burn>0){var fuel=rules.production().fuels().stream().sorted(Comparator.comparingLong(ProductionCatalog.Fuel::burnTicks).reversed()
                .thenComparing(ProductionCatalog.Fuel::itemKind)).findFirst().orElseThrow();
            inputs.merge(fuel.itemKind(),Rate.of(burn,Math.multiplyExact(fuel.burnTicks(),recipe.product().quantity())),Rate::plus);}
        return Map.copyOf(inputs);
    }
    private ProductionRecipeSupplyFlow(){}
}

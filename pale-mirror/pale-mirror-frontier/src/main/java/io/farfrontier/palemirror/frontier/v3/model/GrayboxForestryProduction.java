package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import io.farfrontier.palemirror.frontier.v3.model.tooling.*;
import io.farfrontier.palemirror.frontier.v3.model.supply.*;
import io.farfrontier.palemirror.frontier.v3.model.forestry.ForestryRules;
import java.util.*;

/** New-world content. Uses existing forge, recipe, exact-item, repair and supply owners. */
final class GrayboxForestryProduction {
    private GrayboxForestryProduction() { }
    static FrontierRuleset extend(FrontierRuleset base) {
        String axe = "minecraft:iron_axe";
        var definitions = new TreeMap<>(base.tools().definitions());
        definitions.put(axe, new ToolDefinition(axe, Map.of(ToolAbilities.CHOP, 2), 250, 1500, 1));
        var tools = new ToolCatalog(definitions);
        var recipes = new ArrayList<>(base.production().recipes());
        recipes.add(new ProductionRecipe(new ProductionRecipe.Id("pale_mirror:make_iron_axe"),
                ProductionStationSpec.Capability.FABRICATION,
                new ProductionRecipe.Ingredient(Set.of("minecraft:iron_ingot"), 3),
                new ProductionRecipe.Product(axe, 1), 200, ProductionRecipe.ProcessingMode.ATTENDED,
                new ProductionRecipeOutcome.Individual()));
        recipes.add(new ProductionRecipe(new ProductionRecipe.Id("pale_mirror:repair_iron_axe"),
                ProductionStationSpec.Capability.FABRICATION,
                new ProductionRecipe.Ingredient(Set.of("minecraft:iron_ingot"), 1),
                new ProductionRecipe.Product(axe, 1), 100, ProductionRecipe.ProcessingMode.ATTENDED,
                new ProductionRecipeOutcome.Repair(125)));
        var catalog = new ProductionCatalog(recipes, base.production().fuels(), base.production().equipment(),
                base.production().automaticPolicies());
        var policies = new EnumMap<GoodsPolicyKind, List<GoodsTradeRules.Commodity>>(GoodsPolicyKind.class);
        policies.putAll(base.goodsTrade().policies());
        var goods = new ArrayList<>(policies.get(GoodsPolicyKind.PUBLIC_SETTLEMENT));
        goods.add(new GoodsTradeRules.Commodity(axe, 0, 0, FixedScalar.whole(10), FixedScalar.whole(10),
                new GoodsStockSpecification(GoodsStockSpecification.Representation.INDIVIDUAL, 1000)));
        policies.put(GoodsPolicyKind.PUBLIC_SETTLEMENT, List.copyOf(goods));
        var prior = base.goodsTrade().supplyPlanning();
        var supplies = new TreeMap<>(prior.commodities());
        supplies.put(axe, new SupplyPolicy(1000, 1, 1, prior.require("minecraft:iron_ingot").leadTicks(),
                prior.require("minecraft:iron_ingot").maximumWaitTicks(), true));
        var supply = new SupplyPlanningRules(supplies, prior.maximumOrderItems(), prior.workCargoSlots(),
                prior.personalFoodItems(), prior.maximumTripCargoStacks(), prior.reserves(), prior.sourcing(), prior.economics());
        var trade = new GoodsTradeRules(base.goodsTrade().reviewInterval(), base.goodsTrade().orderLifetime(),
                base.goodsTrade().maximumBatch(), base.goodsTrade().maximumPairReviews(), policies, supply, base.goodsTrade().pricing());
        var work = base.workCatalog().withDefinition(new WorkCatalog.Definition(axe, WorkOperation.PRODUCE, HumanCapability.INDUSTRY, 160))
                .withDefinition(new WorkCatalog.Definition("minecraft:oak_log", WorkOperation.EXTRACT, HumanCapability.EXTRACTION, 160));
        return new FrontierRuleset("frontier-v3-forestry-graybox-r28", base.schemaVersion(), base.cadence(), base.spatial(), base.rates(),
                base.facilityCapacity(), base.combat(), base.hiveCommand(), base.residentLife(),
                base.resourceHarvestColdTravelTicksPerEdge(), work, trade, base.initialSettlementStocks(),
                base.labour().with(ResidentWorkKind.FORESTRY, new SettlementLabourRules.Entry(2, 0, 3)),
                base.expedition(), base.extraction(), base.execution(), catalog, base.fields(), tools, base.worldLayout(), ForestryRules.graybox());
    }
}

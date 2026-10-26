package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import io.farfrontier.palemirror.frontier.v3.model.supply.*;
import java.util.*;

/** Authored vanilla-compatible content. Recipe execution never branches on these material names. */
final class GrayboxWoodProduction {
    static FrontierRuleset extend(FrontierRuleset base) {
        var recipes = new ArrayList<ProductionRecipe>();
        for (var recipe : base.production().recipes()) {
            if (recipe.outcome() instanceof ProductionRecipeOutcome.Individual) {
                var inputs = new ArrayList<>(recipe.ingredients());
                inputs.add(new ProductionRecipe.Ingredient(new ProductionRecipe.InputId("handle"), Set.of("minecraft:stick"), 2));
                recipes.add(new ProductionRecipe(recipe.id(), recipe.capability(), inputs, recipe.product(), recipe.requiresCombustion(),
                        recipe.processingMode(), recipe.outcome()).withNativeCraftingRecipe(new ProductionRecipe.Id(recipe.product().itemKind())));
            } else recipes.add(recipe);
        }
        var planks = new ProductionRecipe.Id("pale_mirror:oak_planks"); var sticks = new ProductionRecipe.Id("pale_mirror:sticks");
        recipes.add(new ProductionRecipe(planks, ProductionStationSpec.Capability.WOODWORKING,
                new ProductionRecipe.Ingredient(Set.of("minecraft:oak_log"), 1), new ProductionRecipe.Product("minecraft:oak_planks", 4),
                false, ProductionRecipe.ProcessingMode.ATTENDED).withNativeCraftingRecipe(new ProductionRecipe.Id("minecraft:oak_planks")));
        recipes.add(new ProductionRecipe(sticks, ProductionStationSpec.Capability.WOODWORKING,
                new ProductionRecipe.Ingredient(Set.of("minecraft:oak_planks"), 2), new ProductionRecipe.Product("minecraft:stick", 4),
                false, ProductionRecipe.ProcessingMode.ATTENDED).withNativeCraftingRecipe(new ProductionRecipe.Id("minecraft:stick")));
        var equipment = new ArrayList<>(base.production().equipment()); equipment.add(ProductionEquipment.woodworking());
        var fuels = new ArrayList<>(base.production().fuels());
        fuels.add(new ProductionCatalog.Fuel("minecraft:oak_log", 300)); fuels.add(new ProductionCatalog.Fuel("minecraft:oak_planks", 300));
        var production = new ProductionCatalog(recipes, fuels, equipment, base.production().automaticPolicies(), List.of(
                new ProductionCatalog.StockPolicy(planks, 64, 16), new ProductionCatalog.StockPolicy(sticks, 64, 16)));
        var trade = base.goodsTrade();
        var publicGoods = new ArrayList<>(trade.policies().get(GoodsPolicyKind.PUBLIC_SETTLEMENT));
        publicGoods.addAll(List.of(new GoodsTradeRules.Commodity("minecraft:oak_log", 0, 0, FixedScalar.whole(2), FixedScalar.whole(2)),
                new GoodsTradeRules.Commodity("minecraft:oak_planks", 0, 0, FixedScalar.ONE, FixedScalar.ONE),
                new GoodsTradeRules.Commodity("minecraft:stick", 0, 0, new FixedScalar(FixedScalar.SCALE / 2), new FixedScalar(FixedScalar.SCALE / 2))));
        var previous = trade.supplyPlanning(); var policies = new TreeMap<>(previous.commodities());
        for (String kind : List.of("minecraft:oak_log", "minecraft:oak_planks", "minecraft:stick"))
            policies.put(kind, previous.require("minecraft:iron_ingot"));
        var tradePolicies = new EnumMap<GoodsPolicyKind, List<GoodsTradeRules.Commodity>>(GoodsPolicyKind.class);
        tradePolicies.putAll(trade.policies()); tradePolicies.put(GoodsPolicyKind.PUBLIC_SETTLEMENT, List.copyOf(publicGoods));
        trade = new GoodsTradeRules(trade.reviewInterval(), trade.orderLifetime(), trade.maximumBatch(), trade.maximumPairReviews(), tradePolicies,
                new SupplyPlanningRules(policies, previous.maximumOrderItems(), previous.workCargoSlots(), previous.personalFoodItems(), previous.maximumTripCargoStacks(),
                        previous.reserves(), previous.sourcing(), previous.economics()), trade.pricing());
        // Preserve the prior roughly 1600-tick/21-loaf processing throughput.
        // The old abstract heat price is not a processing duration; real fuel now burns in elapsed simulation time.
        var work = base.workCatalog().replaceDefinition(new WorkCatalog.Definition("minecraft:bread", WorkOperation.PRODUCE, HumanCapability.INDUSTRY, 80))
                .withDefinition(new WorkCatalog.Definition("minecraft:oak_planks", WorkOperation.PRODUCE, HumanCapability.INDUSTRY, 40))
                .withDefinition(new WorkCatalog.Definition("minecraft:stick", WorkOperation.PRODUCE, HumanCapability.INDUSTRY, 40));
        return new FrontierRuleset("frontier-v3-wood-production-graybox-r29", base.schemaVersion(), base.cadence(), base.spatial(), base.rates(),
                base.facilityCapacity(), base.combat(), base.hiveCommand(), base.residentLife(), base.resourceHarvestColdTravelTicksPerEdge(), work, trade,
                base.initialSettlementStocks(), base.labour(), base.expedition(), base.extraction(), base.execution(), production, base.fields(), base.tools(), base.worldLayout(),
                base.forestry().withStarterEquipment("minecraft:iron_axe", base.forestry().workersPerSettlement()));
    }
    private GrayboxWoodProduction() { }
}

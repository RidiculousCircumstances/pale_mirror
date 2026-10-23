package io.farfrontier.palemirror.frontier.v3.model;

import java.util.*;

/** Initial content only. Recipe execution and equipment selection have no material/tool-name branches. */
final class GrayboxToolProduction {
    static FrontierRuleset extend(FrontierRuleset base) {
        var recipes = new ArrayList<>(base.production().recipes());
        var equipment = new ArrayList<>(base.production().equipment()); equipment.add(ProductionEquipment.forge());
        var work = base.workCatalog();
        var policies = new EnumMap<GoodsPolicyKind, List<GoodsTradeRules.Commodity>>(GoodsPolicyKind.class);
        policies.putAll(base.goodsTrade().policies());
        var commodities = new ArrayList<>(policies.get(GoodsPolicyKind.PUBLIC_SETTLEMENT));
        var supply = base.goodsTrade().supplyPlanning();
        var supplies = new TreeMap<>(supply.commodities());
        for (var material : List.of("stone", "iron")) for (var shape : List.of("pickaxe", "shovel", "hoe")) {
            var item = "minecraft:" + material + "_" + shape;
            String input = material.equals("stone") ? "minecraft:cobblestone" : "minecraft:iron_ingot";
            int quantity = switch (shape) { case "pickaxe" -> 3; case "hoe" -> 2; case "shovel" -> 1;
                default -> throw new IllegalArgumentException("undeclared graybox shape"); };
            long heat = material.equals("stone") ? 0 : 200;
            recipes.add(new ProductionRecipe(new ProductionRecipe.Id("pale_mirror:make_" + material + "_" + shape),
                    ProductionStationSpec.Capability.FABRICATION, new ProductionRecipe.Ingredient(Set.of(input), quantity),
                    new ProductionRecipe.Product(item, 1), heat, ProductionRecipe.ProcessingMode.ATTENDED, new ProductionRecipeOutcome.Individual()));
            recipes.add(new ProductionRecipe(new ProductionRecipe.Id("pale_mirror:repair_" + material + "_" + shape),
                    ProductionStationSpec.Capability.FABRICATION, new ProductionRecipe.Ingredient(Set.of(input), 1),
                    new ProductionRecipe.Product(item, 1), heat / 2, ProductionRecipe.ProcessingMode.ATTENDED,
                    new ProductionRecipeOutcome.Repair(base.tools().require(item).durability() / 2)));
            long seed = material.equals("stone") ? 2L + quantity : 4L + 2L * quantity;
            commodities.add(new GoodsTradeRules.Commodity(item, 0, 0,
                    io.farfrontier.palemirror.frontier.v3.api.FixedScalar.whole(seed),
                    io.farfrontier.palemirror.frontier.v3.api.FixedScalar.whole(seed),
                    new GoodsStockSpecification(GoodsStockSpecification.Representation.INDIVIDUAL, 1000)));
            supplies.put(item, new io.farfrontier.palemirror.frontier.v3.model.supply.SupplyPolicy(1000, 1, 1,
                    supply.require(input).leadTicks(), supply.require(input).maximumWaitTicks(), true));
            work = work.withDefinition(new WorkCatalog.Definition(item, WorkOperation.PRODUCE, HumanCapability.INDUSTRY, 160));
        }
        var production = new ProductionCatalog(recipes, base.production().fuels(), equipment, base.production().automaticPolicies().stream()
                .map(policy -> new ProductionCatalog.AutomaticPolicy(policy.recipeId(), 0, policy.maximumBatches())).toList());
        policies.put(GoodsPolicyKind.PUBLIC_SETTLEMENT, List.copyOf(commodities));
        var trade = new GoodsTradeRules(base.goodsTrade().reviewInterval(), base.goodsTrade().orderLifetime(),
                base.goodsTrade().maximumBatch(), base.goodsTrade().maximumPairReviews(), policies,
                new io.farfrontier.palemirror.frontier.v3.model.supply.SupplyPlanningRules(supplies, supply.maximumOrderItems(),
                    supply.workCargoSlots(), supply.personalFoodItems(), supply.maximumTripCargoStacks(),
                    supply.reserves(), supply.sourcing(), supply.economics()), base.goodsTrade().pricing());
        return new FrontierRuleset("frontier-v3-quarry-graybox-r25", base.schemaVersion(), base.cadence(), base.spatial(), base.rates(),
                base.facilityCapacity(), base.combat(), base.hiveCommand(), base.residentLife(), base.resourceHarvestColdTravelTicksPerEdge(),
                work, trade, base.initialSettlementStocks(), base.labour(), base.expedition(), base.extraction(),
                base.execution(), production, base.fields(), base.tools());
    }
    private GrayboxToolProduction() { }
}

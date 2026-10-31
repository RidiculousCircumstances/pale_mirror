package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import io.farfrontier.palemirror.frontier.v3.model.tooling.*;
import io.farfrontier.palemirror.frontier.v3.model.supply.*;
import java.util.*;

/** New-world equipment content, not a sword-specific production or equipment executor. */
final class GrayboxGuardContent {
    static FrontierRuleset extend(FrontierRuleset base) {
        String sword = "minecraft:iron_sword";
        var definitions = new TreeMap<>(base.tools().definitions());
        definitions.put(sword, new ToolDefinition(sword, Map.of(GuardEquipment.MELEE_WEAPON, 1), 250, 1000, 1));
        var recipes = new ArrayList<>(base.production().recipes());
        recipes.add(new ProductionRecipe(new ProductionRecipe.Id("pale_mirror:make_iron_sword"),
                ProductionStationSpec.Capability.FABRICATION, List.of(
                    new ProductionRecipe.Ingredient(new ProductionRecipe.InputId("blade"), Set.of("minecraft:iron_ingot"), 2),
                    new ProductionRecipe.Ingredient(new ProductionRecipe.InputId("handle"), Set.of("minecraft:stick"), 1)),
                new ProductionRecipe.Product(sword, 1), true, ProductionRecipe.ProcessingMode.ATTENDED,
                new ProductionRecipeOutcome.Individual()).withNativeCraftingRecipe(new ProductionRecipe.Id(sword)));
        recipes.add(new ProductionRecipe(new ProductionRecipe.Id("pale_mirror:repair_iron_sword"),
                ProductionStationSpec.Capability.FABRICATION, new ProductionRecipe.Ingredient(Set.of("minecraft:iron_ingot"), 1),
                new ProductionRecipe.Product(sword, 1), true, ProductionRecipe.ProcessingMode.ATTENDED,
                new ProductionRecipeOutcome.Repair(125)));
        var production = new ProductionCatalog(recipes, base.production().fuels(), base.production().equipment(),
                base.production().automaticPolicies(), base.production().stockPolicies());
        var policies = new EnumMap<GoodsPolicyKind, List<GoodsTradeRules.Commodity>>(GoodsPolicyKind.class);
        policies.putAll(base.goodsTrade().policies());
        var commodities = new ArrayList<>(policies.get(GoodsPolicyKind.PUBLIC_SETTLEMENT));
        commodities.add(new GoodsTradeRules.Commodity(sword, 0, 0, FixedScalar.whole(8), FixedScalar.whole(8),
                new GoodsStockSpecification(GoodsStockSpecification.Representation.INDIVIDUAL, 1000)));
        policies.put(GoodsPolicyKind.PUBLIC_SETTLEMENT, List.copyOf(commodities));
        var prior = base.goodsTrade().supplyPlanning(); var supplies = new TreeMap<>(prior.commodities());
        var iron = prior.require("minecraft:iron_ingot");
        supplies.put(sword, new SupplyPolicy(1000, 1, 1, iron.leadTicks(), iron.maximumWaitTicks(), true));
        var supply = new SupplyPlanningRules(supplies, prior.maximumOrderItems(), prior.workCargoSlots(), prior.personalFoodItems(),
                prior.maximumTripCargoStacks(), prior.reserves(), prior.sourcing(), prior.economics());
        var trade = new GoodsTradeRules(base.goodsTrade().reviewInterval(), base.goodsTrade().orderLifetime(), base.goodsTrade().maximumBatch(),
                base.goodsTrade().maximumPairReviews(), policies, supply, base.goodsTrade().pricing());
        return new FrontierRuleset(base.id(), base.schemaVersion(), base.cadence(), base.spatial(), base.rates(), base.facilityCapacity(),
                base.combat(), base.hiveCommand(), base.residentLife(), base.resourceHarvestColdTravelTicksPerEdge(),
                base.workCatalog().withDefinition(new WorkCatalog.Definition(sword, WorkOperation.PRODUCE, HumanCapability.INDUSTRY, 160)),
                trade, base.initialSettlementStocks(), base.labour().with(ResidentWorkKind.GUARD, new SettlementLabourRules.Entry(2, 2, 2)),
                base.expedition(), base.extraction(), base.execution(), production, base.fields(), new ToolCatalog(definitions), base.worldLayout(), base.forestry());
    }
    private GrayboxGuardContent() { }
}

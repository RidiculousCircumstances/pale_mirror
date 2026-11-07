package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import io.farfrontier.palemirror.frontier.v3.model.combat.RangedRules;
import io.farfrontier.palemirror.frontier.v3.model.supply.*;
import io.farfrontier.palemirror.frontier.v3.model.tooling.*;
import java.util.*;

/** Content declarations only. Common station, supply and equipment owners execute these recipes. */
final class GrayboxRangedContent {
    static FrontierRuleset extend(FrontierRuleset base) {
        String crossbow = "minecraft:crossbow", head = "pale_mirror:iron_arrowhead", arrow = "minecraft:arrow";
        var recipes = new ArrayList<>(base.production().recipes());
        recipes.add(new ProductionRecipe(new ProductionRecipe.Id("pale_mirror:make_crossbow"), ProductionStationSpec.Capability.FABRICATION,
                List.of(input("frame", "minecraft:stick", 4), input("stock", "minecraft:oak_planks", 1),
                    input("mechanism", "minecraft:iron_ingot", 2), input("bowstring", "minecraft:string", 2)),
                new ProductionRecipe.Product(crossbow, 1), true, ProductionRecipe.ProcessingMode.ATTENDED,
                new ProductionRecipeOutcome.Individual()).withNativeCraftingRecipe(new ProductionRecipe.Id(crossbow)));
        recipes.add(new ProductionRecipe(new ProductionRecipe.Id("pale_mirror:repair_crossbow"), ProductionStationSpec.Capability.FABRICATION,
                input("mechanism", "minecraft:iron_ingot", 1), new ProductionRecipe.Product(crossbow, 1), true,
                ProductionRecipe.ProcessingMode.ATTENDED, new ProductionRecipeOutcome.Repair(232)));
        recipes.add(new ProductionRecipe(new ProductionRecipe.Id("pale_mirror:iron_arrowheads"), ProductionStationSpec.Capability.FABRICATION,
                input("metal", "minecraft:iron_ingot", 1), new ProductionRecipe.Product(head, 8), true, ProductionRecipe.ProcessingMode.ATTENDED));
        recipes.add(new ProductionRecipe(new ProductionRecipe.Id("pale_mirror:cloth_arrows"), ProductionStationSpec.Capability.FABRICATION,
                List.of(input("point", head, 4), input("shaft", "minecraft:stick", 4), input("fletching", "pale_mirror:cloth", 1)),
                new ProductionRecipe.Product(arrow, 4), false, ProductionRecipe.ProcessingMode.ATTENDED, new ProductionRecipeOutcome.Fungible()).withNativeCraftingRecipe(new ProductionRecipe.Id(arrow)));
        var stock = new ArrayList<>(base.production().stockPolicies());
        // Supply reserves determine actual demand. These policies admit production, never invent a fixed stock target.
        stock.add(new ProductionCatalog.StockPolicy(new ProductionRecipe.Id("pale_mirror:iron_arrowheads"), 0, 8));
        stock.add(new ProductionCatalog.StockPolicy(new ProductionRecipe.Id("pale_mirror:cloth_arrows"), 0, 32));
        var production = new ProductionCatalog(recipes,base.production().fuels(),base.production().equipment(),base.production().automaticPolicies(),stock);
        var tools = new TreeMap<>(base.tools().definitions());
        tools.put(crossbow, new ToolDefinition(crossbow,Map.of(GuardEquipment.RANGED_WEAPON,1),465,1000,1));
        var policies = new EnumMap<GoodsPolicyKind,List<GoodsTradeRules.Commodity>>(GoodsPolicyKind.class);
        policies.putAll(base.goodsTrade().policies());
        var goods = new ArrayList<>(policies.get(GoodsPolicyKind.PUBLIC_SETTLEMENT));
        goods.add(new GoodsTradeRules.Commodity(crossbow,0,0,FixedScalar.whole(12),FixedScalar.whole(12),
                new GoodsStockSpecification(GoodsStockSpecification.Representation.INDIVIDUAL,1000)));
        goods.add(new GoodsTradeRules.Commodity(head,0,0,new FixedScalar(FixedScalar.SCALE/2),FixedScalar.ONE));
        goods.add(new GoodsTradeRules.Commodity(arrow,0,0,FixedScalar.whole(6),FixedScalar.whole(8)));
        policies.put(GoodsPolicyKind.PUBLIC_SETTLEMENT,List.copyOf(goods));
        var prior=base.goodsTrade().supplyPlanning();var supply=new TreeMap<>(prior.commodities());
        var iron=prior.require("minecraft:iron_ingot");
        supply.put(crossbow,new SupplyPolicy(1000,1,1,iron.leadTicks(),iron.maximumWaitTicks(),true));
        supply.put(head,iron);supply.put(arrow,iron);
        var trade=new GoodsTradeRules(base.goodsTrade().reviewInterval(),base.goodsTrade().orderLifetime(),base.goodsTrade().maximumBatch(),
                base.goodsTrade().maximumPairReviews(),policies,new SupplyPlanningRules(supply,prior.maximumOrderItems(),prior.workCargoSlots(),
                    prior.personalFoodItems(),prior.maximumTripCargoStacks(),prior.reserves(),prior.sourcing(),prior.economics()),base.goodsTrade().pricing());
        var work=base.workCatalog().withDefinition(new WorkCatalog.Definition(crossbow,WorkOperation.PRODUCE,HumanCapability.INDUSTRY,240))
                .withDefinition(new WorkCatalog.Definition(head,WorkOperation.PRODUCE,HumanCapability.INDUSTRY,120))
                .withDefinition(new WorkCatalog.Definition(arrow,WorkOperation.PRODUCE,HumanCapability.INDUSTRY,40));
        return new FrontierRuleset("frontier-v3-ranged-guards-r34",base.schemaVersion(),base.cadence(),base.spatial(),base.rates(),base.facilityCapacity(),
                base.combat(),base.hiveCommand(),base.residentLife(),base.resourceHarvestColdTravelTicksPerEdge(),work,trade,base.initialSettlementStocks(),
                base.labour(),base.expedition(),base.extraction(),base.execution(),production,base.fields(),new ToolCatalog(tools),base.worldLayout(),
                base.forestry(),base.security().withRangedGuards(40),RangedRules.initial());
    }
    private static ProductionRecipe.Ingredient input(String name,String kind,int count) {
        return new ProductionRecipe.Ingredient(new ProductionRecipe.InputId(name),Set.of(kind),count);
    }
    private GrayboxRangedContent() { }
}

package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import io.farfrontier.palemirror.frontier.v3.model.supply.*;
import java.util.*;

/** Graybox content only: cotton agriculture and two attended, fuel-free textile recipes. */
final class GrayboxTextileProduction {
    static FrontierRuleset extend(FrontierRuleset base) {
        var thread = new ProductionRecipe.Id("pale_mirror:cotton_thread");
        var cloth = new ProductionRecipe.Id("pale_mirror:woven_cloth");
        var recipes = new ArrayList<>(base.production().recipes());
        recipes.add(new ProductionRecipe(thread, ProductionStationSpec.Capability.SPINNING,
                new ProductionRecipe.Ingredient(Set.of("pale_mirror:cotton"), 1),
                new ProductionRecipe.Product("minecraft:string", 2), false, ProductionRecipe.ProcessingMode.ATTENDED));
        recipes.add(new ProductionRecipe(cloth, ProductionStationSpec.Capability.WEAVING,
                new ProductionRecipe.Ingredient(Set.of("minecraft:string"), 4),
                new ProductionRecipe.Product("pale_mirror:cloth", 1), false, ProductionRecipe.ProcessingMode.ATTENDED));
        var equipment = new ArrayList<>(base.production().equipment());
        equipment.addAll(List.of(ProductionEquipment.spinning(), ProductionEquipment.weaving()));
        var stock = new ArrayList<>(base.production().stockPolicies());
        stock.addAll(List.of(new ProductionCatalog.StockPolicy(thread, 64, 16), new ProductionCatalog.StockPolicy(cloth, 64, 16)));
        var production = new ProductionCatalog(recipes, base.production().fuels(), equipment, base.production().automaticPolicies(), stock);
        var work = base.workCatalog();
        for (var operation : List.of(WorkOperation.HARVEST, WorkOperation.SOW, WorkOperation.TILL)) {
            var original = work.require("minecraft:wheat", operation);
            work = work.withDefinition(new WorkCatalog.Definition("pale_mirror:cotton", operation, original.capability(), original.workUnits()));
        }
        work = work.withDefinition(new WorkCatalog.Definition("minecraft:string", WorkOperation.PRODUCE, HumanCapability.INDUSTRY, 80))
                .withDefinition(new WorkCatalog.Definition("pale_mirror:cloth", WorkOperation.PRODUCE, HumanCapability.INDUSTRY, 160));
        var prior = base.goodsTrade();
        var commodities = new ArrayList<>(prior.policies().get(GoodsPolicyKind.PUBLIC_SETTLEMENT));
        commodities.addAll(List.of(new GoodsTradeRules.Commodity("pale_mirror:cotton", 0, 0, FixedScalar.ONE, FixedScalar.whole(2)),
                new GoodsTradeRules.Commodity("minecraft:string", 0, 0, FixedScalar.ONE, FixedScalar.whole(2)),
                new GoodsTradeRules.Commodity("pale_mirror:cloth", 0, 0, FixedScalar.whole(4), FixedScalar.whole(6))));
        var policies = new EnumMap<GoodsPolicyKind, List<GoodsTradeRules.Commodity>>(GoodsPolicyKind.class);
        policies.putAll(prior.policies()); policies.put(GoodsPolicyKind.PUBLIC_SETTLEMENT, List.copyOf(commodities));
        var supply = prior.supplyPlanning(); var goods = new TreeMap<>(supply.commodities());
        for (var kind : List.of("pale_mirror:cotton", "minecraft:string", "pale_mirror:cloth")) goods.put(kind, supply.require("minecraft:iron_ingot"));
        var trade = new GoodsTradeRules(prior.reviewInterval(), prior.orderLifetime(), prior.maximumBatch(), prior.maximumPairReviews(), policies,
                new SupplyPlanningRules(goods, supply.maximumOrderItems(), supply.workCargoSlots(), supply.personalFoodItems(), supply.maximumTripCargoStacks(),
                        supply.reserves(), supply.sourcing(), supply.economics()), prior.pricing());
        var entries = new EnumMap<ResidentWorkKind, SettlementLabourRules.Entry>(ResidentWorkKind.class);
        entries.putAll(base.labour().entries()); var industry = entries.get(ResidentWorkKind.PRODUCTION);
        entries.put(ResidentWorkKind.PRODUCTION, new SettlementLabourRules.Entry(industry.targetWorkers() + 1,
                industry.minimumLocalStaff(), industry.priority()));
        var labour = new SettlementLabourRules(entries);
        return new FrontierRuleset("frontier-v3-textiles-terrain-r32", base.schemaVersion(), base.cadence(), base.spatial(), base.rates(),
                base.facilityCapacity(), base.combat(), base.hiveCommand(), base.residentLife(), base.resourceHarvestColdTravelTicksPerEdge(),
                work, trade, base.initialSettlementStocks(), labour, base.expedition(), base.extraction(), base.execution(), production,
                base.fields().withCrop(new FieldBalance.CropPolicy(ResourceSiteKind.COTTON_FIELD, 8, 8, 64)), base.tools(), base.worldLayout(), base.forestry());
    }
    private GrayboxTextileProduction() { }
}

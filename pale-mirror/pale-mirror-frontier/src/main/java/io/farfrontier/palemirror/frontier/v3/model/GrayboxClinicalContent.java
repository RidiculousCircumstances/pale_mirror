package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import io.farfrontier.palemirror.frontier.v3.model.supply.*;
import java.util.*;

/** Medical content uses ordinary crafting, market and supply owners. No starting medicine. */
final class GrayboxClinicalContent {
    static FrontierRuleset extend(FrontierRuleset base) {
        var clinical=base.clinical();
        var recipe=new ProductionRecipe(new ProductionRecipe.Id("pale_mirror:bandages"),ProductionStationSpec.Capability.FABRICATION,
                new ProductionRecipe.Ingredient(new ProductionRecipe.InputId("fabric"),Set.of("pale_mirror:cloth"),1),
                new ProductionRecipe.Product(clinical.bandage(),clinical.bandagesPerCloth()),false,ProductionRecipe.ProcessingMode.ATTENDED)
                .withNativeCraftingRecipe(new ProductionRecipe.Id("pale_mirror:bandages"));
        // Pin our altered recipes by their own native identities. Other packs may retain or replace vanilla recipes.
        var nativeSources=Map.of(new ProductionRecipe.Id("pale_mirror:make_crossbow"),new ProductionRecipe.Id("pale_mirror:make_crossbow"),
                new ProductionRecipe.Id("pale_mirror:cloth_arrows"),new ProductionRecipe.Id("pale_mirror:cloth_arrows"));
        var recipes=new ArrayList<>(base.production().recipes().stream().map(r->nativeSources.containsKey(r.id())
                ?r.withNativeCraftingRecipe(nativeSources.get(r.id())):r).toList());recipes.add(recipe);
        var stock=new ArrayList<>(base.production().stockPolicies());stock.add(new ProductionCatalog.StockPolicy(recipe.id(),0,clinical.supplyLoad()));
        var production=new ProductionCatalog(recipes,base.production().fuels(),base.production().equipment(),base.production().automaticPolicies(),stock);
        var policies=new EnumMap<GoodsPolicyKind,List<GoodsTradeRules.Commodity>>(GoodsPolicyKind.class);policies.putAll(base.goodsTrade().policies());
        var goods=new ArrayList<>(policies.get(GoodsPolicyKind.PUBLIC_SETTLEMENT));
        goods.add(new GoodsTradeRules.Commodity(clinical.bandage(),0,0,FixedScalar.ONE,FixedScalar.whole(2)));
        policies.put(GoodsPolicyKind.PUBLIC_SETTLEMENT,List.copyOf(goods));
        var prior=base.goodsTrade().supplyPlanning();var commodities=new TreeMap<>(prior.commodities());
        commodities.put(clinical.bandage(),prior.require("pale_mirror:cloth"));
        var trade=new GoodsTradeRules(base.goodsTrade().reviewInterval(),base.goodsTrade().orderLifetime(),base.goodsTrade().maximumBatch(),
                base.goodsTrade().maximumPairReviews(),policies,new SupplyPlanningRules(commodities,prior.maximumOrderItems(),prior.workCargoSlots(),
                    prior.personalFoodItems(),prior.maximumTripCargoStacks(),prior.reserves(),prior.sourcing(),prior.economics()),base.goodsTrade().pricing());
        var work=base.workCatalog().withDefinition(new WorkCatalog.Definition(clinical.bandage(),WorkOperation.PRODUCE,HumanCapability.INDUSTRY,40));
        return new FrontierRuleset("frontier-v3-clinical-care-r35",base.schemaVersion(),base.cadence(),base.spatial(),base.rates(),base.facilityCapacity(),
                base.combat(),base.hiveCommand(),base.residentLife(),base.resourceHarvestColdTravelTicksPerEdge(),work,trade,base.initialSettlementStocks(),
                base.labour().with(ResidentWorkKind.MEDICAL,new SettlementLabourRules.Entry(1,1,1)),base.expedition(),base.extraction(),base.execution(),production,
                base.fields(),base.tools(),base.worldLayout(),base.forestry(),base.security(),base.ranged(),clinical);
    }
    private GrayboxClinicalContent(){}
}

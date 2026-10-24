package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.kernel.TransactionRecord;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.extraction.ExtractionPhysicalStep;
import java.util.*;
import static io.farfrontier.palemirror.internal.frontier.v3.FrontierV3EconomyProjection.row;

/** Semantic evidence adapters, never an inference of production from generic stock movement. */
final class FrontierV3EconomyFacts {
    private FrontierV3EconomyFacts() { }
    static String category(FrontierPayload payload) {
        if (payload instanceof ResourceSiteHarvestProgressed) return "HARVEST";
        if (payload instanceof ExtractionWorkProgressed e && e.operation()==ExtractionWorkProgressed.Operation.EXTRACT_BLOCK
                || payload instanceof ExtractionHotObserved hot && hot.step() instanceof ExtractionPhysicalStep.BlockWork) return "EXTRACTION";
        if (payload instanceof ProductionStationRecipeChanged e && e.stage()!=ProductionStationRecipeChanged.Stage.HOT_PREPARED) return "PRODUCTION";
        // The worker can commit the same station conversion before its periodic review.
        // Classify that explicit recipe boundary too; pickup, labour and delivery are not output.
        if (payload instanceof BakeryColdStep e && e.action() == BakeryColdStep.Action.RECIPE
                || payload instanceof BakeryHotEffectObserved hot && hot.phase() == StationWorkState.Phase.PROCESSING
                || payload instanceof BakeryRetiredEffectObserved retired && retired.applied()
                    && retired.effect().phase() == StationWorkState.Phase.PROCESSING) return "PRODUCTION";
        if (payload instanceof ProductionStationFuelChanged e && e.stage()!=ProductionStationFuelChanged.Stage.HOT_PREPARED) return "FUEL";
        if (payload instanceof ResidentMealColdStep e && e.expectedPhase()==ResidentMeal.Phase.CONSUME
                || payload instanceof ResidentMealHotEffectObserved hot && hot.phase()==ResidentMeal.Phase.CONSUME) return "FOOD";
        if (payload instanceof ResidentMealResourceEffectObserved e && e.outcome()==ResidentMealResourceEffectObserved.Outcome.CONSUMPTION_APPLIED) return "FOOD";
        return null;
    }
    static boolean relevant(TransactionRecord tx, FrontierWorldState before, FrontierWorldState after) {
        if (before.companies().goodsTrade().priceHistory()!=after.companies().goodsTrade().priceHistory()) return true;
        for (var event:tx.events()) if (category(event.payload())!=null) return true;
        return false;
    }
    static List<Map<String,Object>> facts(TransactionRecord tx, FrontierWorldState before, FrontierWorldState after) {
        var result=new ArrayList<Map<String,Object>>();
        if(before.companies().goodsTrade().priceHistory()!=after.companies().goodsTrade().priceHistory()) {
            var previous=new HashSet<SubjectId>();
            before.companies().goodsTrade().priceHistory().commodities().values().forEach(sales->sales.forEach(s->previous.add(s.receipt())));
            after.companies().goodsTrade().priceHistory().commodities().forEach((item,sales)->sales.stream()
                    .filter(s->!previous.contains(s.receipt())).forEach(s->result.add(row("id",s.receipt().value(),"kind","sale",
                            "tick",s.atTick(),"revision",tx.revision().value(),"seller",s.seller(),"buyer",s.buyer(),"item",item,
                            "quantity",s.quantity(),"goodsUnitRaw",s.unitPrice().raw(),"deliveryUnitRaw",s.deliveryUnitPrice().raw()))));
        }
        var categories=tx.events().stream().map(e->category(e.payload())).filter(Objects::nonNull).distinct().toList();
        if(categories.size()!=1) {
            if(categories.size()>1) result.add(row("id",tx.id().value()+":ambiguous","kind","gap","tick",tx.instant().ticks(),
                    "revision",tx.revision().value(),"reason","mixed_semantic_flows"));
            return result;
        }
        // Compare total ownership only inside a confirmed semantic conversion. Transfers,
        // reservations, taking stock, player changes and promises never enter this adapter.
        var delta=totals(after); totals(before).forEach((key,value)->delta.merge(key,-value,Long::sum));
        int ordinal=0;
        for(var entry:delta.entrySet()) if(entry.getValue()!=0) {
            var key=entry.getKey(); long quantity=entry.getValue();
            result.add(row("id",tx.id().value()+":flow:"+(ordinal++),"kind","flow","tick",tx.instant().ticks(),
                    "revision",tx.revision().value(),"town",key.owner(),"item",key.item(),
                    "category",categories.getFirst()+ (quantity>0?":OUTPUT":":INPUT"),"quantity",Math.abs(quantity)));
        }
        return result;
    }
    private static Map<FrontierV3EconomyProjection.StockKey,Long> totals(FrontierWorldState state) {
        var result=new HashMap<FrontierV3EconomyProjection.StockKey,Long>();
        for(var lot:state.inventory().fungibleResources().lots().values())
            result.merge(new FrontierV3EconomyProjection.StockKey(lot.economicOwnerId(),lot.itemKind()),(long)lot.quantity(),Long::sum);
        for(var item:state.inventory().items().values())
            result.merge(new FrontierV3EconomyProjection.StockKey(item.economicOwnerId(),item.itemKind()),(long)item.count(),Long::sum);
        return result;
    }
}

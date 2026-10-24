package io.farfrontier.palemirror.internal.frontier.v3;

import com.google.gson.Gson;
import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.kernel.TransactionRecord;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.market.MarketPriceHistory;
import io.farfrontier.palemirror.frontier.v3.model.execution.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FrontierV3EconomyProjectionTest {
    @Test void workerCommittedRecipeExportsActualConversionButTransfersDoNot() {
        var initial = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:analytics-recipe"),126));
        var home = initial.bootstrap().settlements().getFirst().id();
        var original = initial.inventory().fungibleResources(); var input = new SubjectId("lot:analytics-input");
        var holder = original.accounts().values().stream().filter(a -> a.custody().equals(
                new ResourceCustody.Container(FrontierWorldState.depotId(home)))).findFirst().orElseThrow();
        var account = holder.id(); var lots = new HashMap<>(original.lots());
        lots.put(input, new ResourceLot(input, home, "minecraft:wheat", 3, "test", List.of()));
        var quantities = new HashMap<>(holder.lotQuantities()); quantities.put(input, 3);
        var accounts = new HashMap<>(original.accounts()); accounts.put(account, holder.withQuantities(quantities, holder.claimQuantities()));
        var ledger = new FungibleResourceLedger(lots, original.claims(), accounts, original.bindings());
        var before = initial.withInventory(initial.inventory().withFungibleResources(ledger));
        var output = new ResourceLot(new SubjectId("lot:analytics-output"), home, "minecraft:bread", 1, "test", List.of(input));
        var after = before.withInventory(before.inventory().withFungibleResources(ledger.transformCold(account, Map.of(input, 3), Map.of(), output)));
        var job = new SubjectId("job:analytics"); var actor = new SubjectId("resident:analytics");
        var execution = new ActorExecutionId(actor, ActorActivityKind.PRODUCTION, job, 1);
        var cold = new BakeryColdStep(job, StationWorkState.Phase.PROCESSING, BakeryColdStep.Action.RECIPE,
                SurfaceAnchor.at(0, 63, 0), execution, Optional.empty());
        var hot = new BakeryHotEffectObserved(job, new SceneLeaseId("lease:analytics"), StationWorkState.Phase.PROCESSING,
                new BodyPosition(0, 64, 0), 1, 1, List.of(), List.of());
        var identity = new ActorActuationId(new ActorBodyId(actor, 1), execution);
        for (FrontierPayload payload : List.of(cold, hot, new BakeryRetiredEffectObserved(identity, hot, true))) {
            var id = new TransactionId("transaction:recipe"); var revision = new Revision(1); var at = new SimInstant(1);
            var event = new FrontierEvent(1, new EventId("event:recipe"), id, initial.bootstrap().worldId(), revision, at,
                    home, CauseChain.root(new CommandId("command:recipe")), payload);
            var tx = new TransactionRecord(id, initial.bootstrap().worldId(), revision, at, List.of(event), Optional.empty());
            var facts = FrontierV3EconomyFacts.facts(tx, before, after);
            assertEquals(2, facts.size());
            assertTrue(facts.stream().anyMatch(f -> f.get("category").equals("PRODUCTION:OUTPUT") && f.get("quantity").equals(1L)));
            assertTrue(facts.stream().anyMatch(f -> f.get("category").equals("PRODUCTION:INPUT") && f.get("quantity").equals(3L)));
            assertTrue(FrontierV3EconomyFacts.facts(tx, after, after).isEmpty(), "no invented output from unchanged stock");
        }
        assertNull(FrontierV3EconomyFacts.category(new BakeryRetiredEffectObserved(identity, hot, false)));
        assertNull(FrontierV3EconomyFacts.category(new BakeryColdStep(job, StationWorkState.Phase.DEPOT_DELIVERY,
                BakeryColdStep.Action.DELIVER, SurfaceAnchor.at(0, 63, 0), execution, Optional.empty())));
    }
    @Test void snapshotsReconcileOwnedInventoryWithoutChangingCanonicalState() {
        var state=FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:analytics"),126));
        var inventory=state.inventory();var snapshot=FrontierV3EconomyProjection.snapshot(state,0,0,"ACTIVE");
        assertEquals(state.bootstrap().settlements().size(),((List<?>)snapshot.get("settlements")).size());
        long actual=FrontierV3EconomyProjection.stocks(state).stream().mapToLong(row->(long)row.get("total")).sum();
        long expected=inventory.fungibleResources().lots().values().stream().mapToLong(ResourceLot::quantity).sum()
                +inventory.items().values().stream().mapToLong(ExactItemStack::count).sum();
        assertEquals(expected,actual);assertSame(inventory,state.inventory());
        assertTrue(new Gson().toJson(snapshot).contains("availableRaw"));
        var first = (Map<?,?>)((List<?>)snapshot.get("settlements")).getFirst();
        assertEquals(state.bootstrap().settlements().getFirst().anchor().x(), first.get("x"));
        assertEquals(state.bootstrap().settlements().getFirst().anchor().z(), first.get("z"));
        var map = (Map<?,?>)snapshot.get("map");
        assertEquals(state.bootstrap().bounds().width(), ((Map<?,?>)map.get("bounds")).get("width"));
        assertEquals(List.of(), map.get("missions"), "no invented caravans in an empty mission registry");
    }
    @Test void distantDisplayRouteIsBoundedAndRetainsBothEndpointsWithoutExpandingGeometry() {
        var route = java.util.stream.IntStream.range(0, 100_000).mapToObj(x -> SurfaceAnchor.at(x, 63, -x)).toList();
        var projected = FrontierV3EconomyMapProjection.route(route);
        assertEquals(FrontierV3EconomyMapProjection.MAX_ROUTE_POINTS, projected.size());
        assertEquals(0, projected.getFirst().get("x")); assertEquals(99_999, projected.getLast().get("x"));
        assertEquals(-99_999, projected.getLast().get("z"));
        assertTrue(FrontierV3EconomyMapProjection.route(List.of()).isEmpty());
        assertEquals(1, FrontierV3EconomyMapProjection.route(List.of(SurfaceAnchor.at(2, 64, 3))).size());
    }
    @Test void onlyNewConfirmedSalesAreExportedWithSeparateDeliveryAndStableReceiptIdentity() {
        var before=FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:analytics-sales"),126));
        var a=before.bootstrap().settlements().get(0).id();var b=before.bootstrap().settlements().get(1).id();
        var sale=new MarketPriceHistory.Sale(new SubjectId("receipt:analytics"),a,b,1,3,FixedScalar.whole(2),FixedScalar.whole(1));
        var trade=before.companies().goodsTrade();
        var after=before.withCompanies(before.companies().withGoodsTrade(new GoodsTradeState(trade.orders(),trade.contracts(),trade.participants(),trade.priceHistory().record("minecraft:bread",sale))));
        var id=new TransactionId("transaction:analytics");var rev=new Revision(1);var at=new SimInstant(1);
        FrontierPayload observation=new FrontierPayload(){public String type(){return "test.sale";}};
        var event=new FrontierEvent(1,new EventId("event:analytics"),id,before.bootstrap().worldId(),rev,at,a,CauseChain.root(new CommandId("command:analytics")),observation);
        var tx=new TransactionRecord(id,before.bootstrap().worldId(),rev,at,List.of(event),Optional.empty());
        var facts=FrontierV3EconomyFacts.facts(tx,before,after);assertEquals(1,facts.size());
        assertEquals("receipt:analytics",facts.getFirst().get("id"));assertEquals(3,facts.getFirst().get("quantity"));
        assertEquals(FixedScalar.whole(2).raw(),facts.getFirst().get("goodsUnitRaw"));
        assertEquals(FixedScalar.whole(1).raw(),facts.getFirst().get("deliveryUnitRaw"));
        assertTrue(FrontierV3EconomyFacts.facts(tx,after,after).isEmpty());
        assertNull(FrontierV3EconomyFacts.category(observation));
    }
}

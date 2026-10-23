package io.farfrontier.palemirror.internal.frontier.v3;

import com.google.gson.Gson;
import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.kernel.TransactionRecord;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.market.MarketPriceHistory;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FrontierV3EconomyProjectionTest {
    @Test void snapshotsReconcileOwnedInventoryWithoutChangingCanonicalState() {
        var state=FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:analytics"),126));
        var inventory=state.inventory();var snapshot=FrontierV3EconomyProjection.snapshot(state,0,0,"ACTIVE");
        assertEquals(state.bootstrap().settlements().size(),((List<?>)snapshot.get("settlements")).size());
        long actual=FrontierV3EconomyProjection.stocks(state).stream().mapToLong(row->(long)row.get("total")).sum();
        long expected=inventory.fungibleResources().lots().values().stream().mapToLong(ResourceLot::quantity).sum()
                +inventory.items().values().stream().mapToLong(ExactItemStack::count).sum();
        assertEquals(expected,actual);assertSame(inventory,state.inventory());
        assertTrue(new Gson().toJson(snapshot).contains("availableRaw"));
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

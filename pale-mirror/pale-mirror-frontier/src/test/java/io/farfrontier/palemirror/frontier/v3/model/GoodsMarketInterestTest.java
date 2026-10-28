package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GoodsMarketInterestTest {
    @Test void duplicateCommodityInterestsRetainTheirLatestExpiryAndWithdrawalRestoresIndependentBranches() {
        var state = GoodsTradeTest.initial(); var participants = state.companies().goodsTrade().participants();
        var parties = state.bootstrap().settlements().stream().limit(3)
                .map(home -> participants.participants().get(home.id()).party()).toList();
        var seller = parties.get(0); var buyer = parties.get(1); var other = parties.get(2);
        var ask = order("ask", seller, GoodsTradeOrder.Side.SELL, "minecraft:bread", 40);
        var early = order("early", buyer, GoodsTradeOrder.Side.BUY, "minecraft:bread", 20);
        var late = order("late", buyer, GoodsTradeOrder.Side.BUY, "minecraft:bread", 40);
        var trade = new GoodsTradeState(Map.of(), Map.of(), participants).place(ask).place(early).place(late)
                .place(order("foreign", other, GoodsTradeOrder.Side.BUY, "minecraft:coal", 40))
                .place(order("self", seller, GoodsTradeOrder.Side.BUY, "minecraft:bread", 40));
        assertEquals(List.of(buyer), GoodsMarketInterest.recipients(trade, ask, OptionalLong.of(21)));
        assertEquals(List.of(seller), GoodsMarketInterest.recipients(trade, early, OptionalLong.of(21)));
        assertTrue(GoodsMarketInterest.recipients(trade, ask, OptionalLong.of(41)).isEmpty());
        var withdrawn = trade.withdraw(buyer.id(), new GoodsTradeOrderWithdrawn(late.id(), 0));
        assertTrue(GoodsMarketInterest.recipients(withdrawn, ask, OptionalLong.of(21)).isEmpty());
        assertEquals(List.of(buyer), GoodsMarketInterest.recipients(withdrawn, ask, OptionalLong.empty()),
                "undated invalidation keeps the existing interest semantics");
        assertEquals(List.of(buyer), GoodsMarketInterest.recipients(trade, ask, OptionalLong.of(21)),
                "a later withdrawal cannot poison an older immutable market branch");
    }
    private static GoodsTradeOrder order(String name, GoodsTradeParty party, GoodsTradeOrder.Side side, String kind, long expiry) {
        return new GoodsTradeOrder(new SubjectId("order:interest-" + name), party, side,
                FrontierWorldState.depotId(party.id()), kind, 64, 0, FixedScalar.ONE, expiry);
    }
}

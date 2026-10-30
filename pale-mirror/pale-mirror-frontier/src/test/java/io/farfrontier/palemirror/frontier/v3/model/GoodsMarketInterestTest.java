package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GoodsMarketInterestTest {
    @Test void retiringOldCommitmentHistoryPreservesTheSameLiveOfferButNewReservationsChangeIt() {
        var state = GoodsTradeTest.initial(); var participants = state.companies().goodsTrade().participants();
        var parties = state.bootstrap().settlements().stream().limit(2)
                .map(home -> participants.participants().get(home.id()).party()).toList();
        var seller = parties.get(0); var buyer = participants.participants().get(parties.get(1).id());
        var open = priced("live-history", seller, GoodsTradeOrder.Side.SELL, 1, 100).commit(32);
        var before = new GoodsTradeState(Map.of(open.id(), open), Map.of(), participants);
        var after = new GoodsTradeState(Map.of(open.id(), open.retireCommitment(32)), Map.of(), participants);
        var limits = Map.of("minecraft:bread", FixedScalar.ONE);
        var captured = GoodsMarketInterest.purchaseOffers(before, buyer, limits, 20);
        assertFalse(captured.isEmpty());
        assertEquals(captured, GoodsMarketInterest.purchaseOffers(after, buyer, limits, 20),
                "retired historical contracts change no remaining quantity, price, endpoint or consent identity");
        var reserved = new GoodsTradeState(Map.of(open.id(), open.commit(1)), Map.of(), participants);
        assertNotEquals(captured, GoodsMarketInterest.purchaseOffers(reserved, buyer, limits, 20));
        assertEquals(Map.of(), GoodsMarketInterest.purchaseOffers(after, buyer, limits, 101));
    }
    @Test void immediateMatchingUsesLivePriceConsentWithoutDiscardingEconomicInterest() {
        var state = GoodsTradeTest.initial(); var participants = state.companies().goodsTrade().participants();
        var parties = state.bootstrap().settlements().stream().limit(3)
                .map(home -> participants.participants().get(home.id()).party()).toList();
        var seller = parties.get(0); var buyer = parties.get(1); var other = parties.get(2);
        var ask = priced("ask", seller, GoodsTradeOrder.Side.SELL, 10, 40);
        var expiredCrossing = priced("expired", buyer, GoodsTradeOrder.Side.BUY, 11, 20);
        var low = priced("low", buyer, GoodsTradeOrder.Side.BUY, 9, 50);
        var equal = priced("equal", other, GoodsTradeOrder.Side.BUY, 10, 40);
        var committed = priced("committed", buyer, GoodsTradeOrder.Side.BUY, 12, 100).commit(64);
        var trade = new GoodsTradeState(Map.of(), Map.of(), participants).place(ask).place(expiredCrossing)
                .place(low).place(equal).place(priced("committed", buyer, GoodsTradeOrder.Side.BUY, 12, 100));
        var orders = new HashMap<>(trade.orders()); orders.put(committed.id(), committed);
        trade = new GoodsTradeState(orders, Map.of(), participants);
        assertEquals(List.of(other), GoodsMarketInterest.matchingRecipients(trade, ask, 21));
        assertEquals(List.of(buyer, other), GoodsMarketInterest.recipients(trade, ask, OptionalLong.of(21)),
                "noncrossing prices must still invalidate the economic observation");
        assertEquals(List.of(seller), GoodsMarketInterest.matchingRecipients(trade, equal, 21));
        assertEquals(List.of(other), GoodsMarketInterest.matchingRecipients(trade, ask, 40),
                "the publication filter uses the same inclusive expiry as the order book");
        assertTrue(GoodsMarketInterest.matchingRecipients(trade, ask, 41).isEmpty());
        var withdrawn = trade.withdraw(other.id(), new GoodsTradeOrderWithdrawn(equal.id(), 0));
        assertTrue(GoodsMarketInterest.matchingRecipients(withdrawn, ask, 21).isEmpty());
        assertEquals(List.of(other), GoodsMarketInterest.matchingRecipients(trade, ask, 21),
                "withdrawal cannot change a prior immutable branch");
        var captured = trade;
        assertThrows(IllegalArgumentException.class, () -> GoodsMarketInterest.matchingRecipients(captured, ask, -1));
    }
    private static GoodsTradeOrder priced(String name, GoodsTradeParty party, GoodsTradeOrder.Side side, long price, long expiry) {
        return new GoodsTradeOrder(new SubjectId("order:crossing-" + name), party, side,
                FrontierWorldState.depotId(party.id()), "minecraft:bread", 64, 0, FixedScalar.whole(price), expiry);
    }
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

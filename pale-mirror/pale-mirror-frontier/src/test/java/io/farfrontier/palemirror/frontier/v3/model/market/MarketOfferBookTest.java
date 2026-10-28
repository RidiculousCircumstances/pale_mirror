package io.farfrontier.palemirror.frontier.v3.model.market;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class MarketOfferBookTest {
    @Test void purchaserComparesCurrentPublicAsksFromIndependentSellersInPriceOrder() {
        var seller = party("seller"); var buyer = party("buyer"); var other = party("other");
        var cheap = order("cheap", seller, GoodsTradeOrder.Side.SELL, 2, 20);
        var costly = order("costly", seller, GoodsTradeOrder.Side.SELL, 4, 20);
        var bid = order("bid", buyer, GoodsTradeOrder.Side.BUY, 6, 20);
        var competitor = order("competitor", other, GoodsTradeOrder.Side.SELL, 1, 20);
        var book = new MarketOfferBook(List.of(costly, bid, cheap, competitor,
                order("expired", seller, GoodsTradeOrder.Side.SELL, 1, 9),
                order("unmatched", other, GoodsTradeOrder.Side.BUY, 5, 9)), 10);
        assertEquals(List.of(competitor, cheap, costly), book.offers(buyer, Optional.empty(), Set.of()).stream().map(MarketOfferBook.Pair::sell).toList());
        assertEquals(other, book.offers(buyer, Optional.empty(), Set.of()).getFirst().sell().party());
        assertEquals(Set.of(buyer), book.buyers());
        assertFalse(book.validAt(9), "an earlier branch may include already expired quotes");
        assertTrue(book.validAt(10)); assertTrue(book.validAt(20));
        assertFalse(book.validAt(21), "a captured book cannot retain quotes beyond their exact expiry");
        assertEquals(Set.of(seller, other), book.sellers(buyer));
        assertTrue(book.sellers(other).isEmpty());
        assertTrue(book.offers(other, Optional.empty(), Set.of()).isEmpty());
        assertEquals(List.of(competitor), book.offers(buyer, Optional.of(other), Set.of()).stream().map(MarketOfferBook.Pair::sell).toList());
        assertTrue(book.offers(buyer, Optional.of(party("unknown")), Set.of()).isEmpty());
        assertTrue(book.offers(buyer, Optional.empty(), Set.of("minecraft:bread")).isEmpty());
        var expired = new MarketOfferBook(List.of(costly, bid, cheap, competitor), 21);
        assertTrue(expired.offers(buyer, Optional.of(seller), Set.of()).isEmpty());
        assertTrue(expired.validAt(Long.MAX_VALUE), "an unchanged empty book cannot acquire future supply by waiting");
    }
    @Test void onePublicAskSharesItsRemainderAcrossManyBuyersWithoutDuplicatedSupply() {
        var seller = party("seller"); var ask = order("shared", seller, GoodsTradeOrder.Side.SELL, 2, 20);
        var orders = new ArrayList<GoodsTradeOrder>(); orders.add(ask);
        for (int i = 0; i < 100; i++) orders.add(order("bid-" + i, party("buyer-" + i), GoodsTradeOrder.Side.BUY, 3, 20));
        var first = new MarketOfferBook(orders, 10);
        assertEquals(100, first.buyers().size());
        assertEquals(101, orders.size(), "one offer is not copied for every known participant");
        var remaining = ask.commit(60); orders.set(0, remaining);
        var partial = new MarketOfferBook(orders, 10);
        for (var buyer : partial.buyers()) assertEquals(4, partial.offers(buyer, Optional.empty(), Set.of()).getFirst().sell().availableQuantity());
        orders.set(0, remaining.commit(4));
        var filled = new MarketOfferBook(orders, 10);
        for (var buyer : filled.buyers()) {
            assertTrue(filled.offers(buyer, Optional.empty(), Set.of()).isEmpty());
            assertTrue(filled.sellers(buyer).isEmpty());
        }
        assertThrows(IllegalArgumentException.class, () -> remaining.commit(5));
    }

    private static GoodsTradeParty party(String name) {
        return new GoodsTradeParty(new SubjectId("party:" + name), EconomicOwnerKind.SETTLEMENT_TREASURY);
    }
    private static GoodsTradeOrder order(String name, GoodsTradeParty party,
            GoodsTradeOrder.Side side, long price, long expiry) {
        return new GoodsTradeOrder(new SubjectId("order:" + name), party, side,
                new SubjectId("container:" + name), "minecraft:bread", 64, 0, FixedScalar.whole(price), expiry);
    }
}

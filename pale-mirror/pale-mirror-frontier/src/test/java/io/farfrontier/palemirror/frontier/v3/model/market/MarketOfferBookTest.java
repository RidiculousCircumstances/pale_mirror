package io.farfrontier.palemirror.frontier.v3.model.market;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class MarketOfferBookTest {
    @Test void purchaserReceivesOnlyMutualCurrentQuotesInPriceOrder() {
        var seller = party("seller"); var buyer = party("buyer"); var other = party("other");
        var cheap = order("cheap", seller, buyer, GoodsTradeOrder.Side.SELL, 2, 20);
        var costly = order("costly", seller, buyer, GoodsTradeOrder.Side.SELL, 4, 20);
        var bid = order("bid", buyer, seller, GoodsTradeOrder.Side.BUY, 6, 20);
        var book = new MarketOfferBook(List.of(costly, bid, cheap,
                order("expired", seller, buyer, GoodsTradeOrder.Side.SELL, 1, 9),
                order("foreign", seller, other, GoodsTradeOrder.Side.SELL, 1, 20),
                order("unmatched", other, seller, GoodsTradeOrder.Side.BUY, 5, 9)), 10);
        assertEquals(List.of(cheap, costly), book.offers(buyer, Optional.empty(), Set.of()).stream().map(MarketOfferBook.Pair::sell).toList());
        assertEquals(Set.of(buyer), book.buyers());
        assertTrue(book.offers(other, Optional.empty(), Set.of()).isEmpty());
        assertTrue(book.offers(buyer, Optional.of(other), Set.of()).isEmpty());
        assertTrue(book.offers(buyer, Optional.empty(), Set.of("minecraft:bread")).isEmpty());
    }
    private static GoodsTradeParty party(String name) {
        return new GoodsTradeParty(new SubjectId("party:" + name), EconomicOwnerKind.SETTLEMENT_TREASURY);
    }
    private static GoodsTradeOrder order(String name, GoodsTradeParty party, GoodsTradeParty counterparty,
            GoodsTradeOrder.Side side, long price, long expiry) {
        return new GoodsTradeOrder(new SubjectId("order:" + name), party, counterparty, side,
                new SubjectId("container:" + name), "minecraft:bread", 64, 0, FixedScalar.whole(price), expiry);
    }
}

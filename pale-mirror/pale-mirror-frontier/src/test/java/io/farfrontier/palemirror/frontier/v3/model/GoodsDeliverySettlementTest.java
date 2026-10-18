package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldPayloadCodecs;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static io.farfrontier.palemirror.frontier.v3.model.GoodsTradeTest.*;

/** Financial delivery terms reuse the existing canonical trade setup, not a second transport fixture. */
class GoodsDeliverySettlementTest {
    @Test void deliveryExpenseSettlesOnceWithGoodsWhileMarketPriceRemainsMerchandiseOnly() {
        var state = fact(fact(initial(), SELLER, new GoodsTradeOrderPlaced(sellOrder())), BUYER, new GoodsTradeOrderPlaced(buyOrder()));
        var original = reservation();
        var quoted = new GoodsTradeReserved(original.contract().quoteDelivery(new FixedScalar(FixedScalar.SCALE / 2)), original.allocations());
        var codecs = FrontierWorldPayloadCodecs.create();
        assertEquals(quoted, codecs.decode(quoted.type(), codecs.encode(quoted)));
        var offered = state;
        assertThrows(IllegalArgumentException.class, () -> fact(offered, SELLER,
                new GoodsTradeReserved(original.contract().quoteDelivery(FixedScalar.whole(3)), original.allocations())),
                "urgency cannot override the buyer's delivered price limit");
        state = fact(state, SELLER, quoted);
        var buyerBefore = state.inventory().economics().require(BUYER).balance();
        var sellerBefore = state.inventory().economics().require(SELLER).balance();
        assertEquals(FixedScalar.whole(90), state.inventory().economics().reservations().get(HOLD).amount());
        state = arrive(state);
        var first = new GoodsTradeAccepted(receipt("receipt:freight-first", 0, 20, "lot:freight-first"));
        state = fact(state, BUYER, first);
        assertEquals(buyerBefore.minus(FixedScalar.whole(30)), state.inventory().economics().require(BUYER).balance());
        assertEquals(sellerBefore.plus(FixedScalar.whole(30)), state.inventory().economics().require(SELLER).balance());
        assertEquals(FixedScalar.whole(60), state.inventory().economics().reservations().get(HOLD).amount());
        var codec = new FrontierWorldStateCodec(); state = codec.decode(codec.encode(state));
        var recovered = state;
        assertThrows(IllegalArgumentException.class, () -> fact(recovered, BUYER, first));
        state = fact(state, BUYER, new GoodsTradeAccepted(receipt("receipt:freight-rest", 1, 40, "lot:freight-rest")));
        assertEquals(buyerBefore.minus(FixedScalar.whole(90)), state.inventory().economics().require(BUYER).balance());
        assertEquals(sellerBefore.plus(FixedScalar.whole(90)), state.inventory().economics().require(SELLER).balance());
        var history = state.companies().goodsTrade().priceHistory();
        assertEquals(PRICE, history.reference("minecraft:wheat", 24000).orElseThrow());
        assertEquals(FixedScalar.whole(90).raw(), history.commodities().get("minecraft:wheat").stream()
                .mapToLong(sale -> sale.totalPrice().raw()).sum());
        assertEquals(state, codec.decode(codec.encode(state)));
    }
}

package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GoodsTripFeasibilityTest {
    @Test void cheapFirstAllocationSurvivesAnUnaffordableAverageAndOtherUnfundedLines() {
        var state = ConsolidatedTradeFixture.initial(new WorldId("frontier:first-allocation"), 20260918065L, false);
        var seller = state.companies().goodsTrade().participants().participants().get(new SubjectId("settlement:1"));
        var buyer = state.companies().goodsTrade().participants().participants().get(seller.known().getFirst().party().id());
        var source = FrontierWorldState.depotId(seller.settlementId()); var receiver = FrontierWorldState.depotId(buyer.settlementId());
        var trade = state.companies().goodsTrade();
        for (var order : List.of(
                new GoodsTradeOrder(new SubjectId("order:first-cheap"), seller.party(), GoodsTradeOrder.Side.SELL, source,
                        "minecraft:bread", 1, 0, FixedScalar.ONE, 1000),
                new GoodsTradeOrder(new SubjectId("order:first-dear"), seller.party(), GoodsTradeOrder.Side.SELL, source,
                        "minecraft:bread", 63, 0, FixedScalar.whole(10), 1000),
                new GoodsTradeOrder(new SubjectId("order:first-bid"), buyer.party(), GoodsTradeOrder.Side.BUY, receiver,
                        "minecraft:bread", 64, 0, FixedScalar.whole(10), 1000))) trade = trade.place(order);
        state = state.withCompanies(state.companies().withGoodsTrade(trade));
        var quote = GoodsTripQuotes.read(state, seller, buyer, new GoodsOrderMatching.Endpoints(source, receiver), 1);
        assertEquals(FixedScalar.ONE, quote.minimumPrices().get("minecraft:bread"));
        assertTrue(quote.lines().get("minecraft:bread").unitPrice().compareTo(FixedScalar.ONE) > 0);
        var stocks = Map.of("minecraft:bread", new GoodsParticipantView.Stock(0, 0, 0),
                "minecraft:coal", new GoodsParticipantView.Stock(0, 0, 0));
        var template = state.bootstrap().ruleset().goodsTrade();
        var rules = new GoodsTradeRules(template.reviewInterval(), template.orderLifetime(), template.maximumBatch(),
                template.maximumPairReviews(), template.policies());
        var view = new GoodsParticipantView(buyer, 1, FixedScalar.ONE, stocks);
        var funding = GoodsParticipantFunding.purchases(view, rules);
        assertTrue(funding.canStart(Map.of("minecraft:bread", FixedScalar.ONE, "minecraft:coal", FixedScalar.whole(10))),
                "one possible first allocation suffices; later funding still belongs to exact matching");
        assertFalse(funding.canStart(Map.of("minecraft:bread", FixedScalar.whole(2))));
        assertFalse(funding.canStart(Map.of()));
        assertFalse(GoodsParticipantFunding.purchases(new GoodsParticipantView(buyer, 1, FixedScalar.ZERO, stocks), rules)
                .canStart(quote.minimumPrices()), "a new cash snapshot cannot borrow earlier affordability");
        assertTrue(state.companies().goodsTrade().contracts().isEmpty());
        assertTrue(state.shipments().missions().isEmpty(), "preliminary funding grants no reservation or mission");
    }
}

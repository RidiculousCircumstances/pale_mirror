package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.supply.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class SettlementSourcingMarketTest {
    @Test void firstAffordableQuoteCanRankWithoutAnExistingCaravanAndWithoutInventingIncomingStock() {
        var state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:first-economic-quote"),
                125L, FrontierRulesets.marketQuarryGraybox()));
        var trade = state.companies().goodsTrade();
        var buyer = trade.participants().participants().get(new SubjectId("settlement:6"));
        var seller = trade.participants().participants().get(new SubjectId("settlement:7"));
        var container = GoodsParticipantStorage.containers(state, seller, "minecraft:bread").stream().sorted().findFirst().orElseThrow();
        var offer = new GoodsTradeOrder(new SubjectId("order:first-economic-bread"), seller.party(), buyer.party(),
                GoodsTradeOrder.Side.SELL, container, "minecraft:bread", 32, 0, new FixedScalar(100000), 10000);
        state = state.withCompanies(state.companies().withGoodsTrade(trade.place(offer)));
        assertTrue(state.shipments().missions().isEmpty());
        var process = new SupplyPlanner.Process("fixture:expensive-production", "minecraft:bread", 1, Map.of(),
                0, Map.of(), 0, 1, 1, 32, 100000);
        var market = SettlementSourcingMarket.read(state, buyer, List.of(process));
        assertEquals(1, market.offers().size());
        assertTrue(market.offers().getFirst().tripCost() > 0);
        assertEquals(32, market.allocate().preferPurchase("minecraft:bread", 32, Long.MAX_VALUE, Optional.of(process)));
        var codec = new io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec();
        var restored = codec.decode(codec.encode(state));
        assertEquals(market, SettlementSourcingMarket.read(restored, buyer, List.of(process)));
        assertTrue(state.shipments().missions().isEmpty());
        assertTrue(SettlementDeliveryForecast.read(state, buyer).values().stream().allMatch(List::isEmpty));
        assertEquals(state.inventory(), restored.inventory());
    }
}

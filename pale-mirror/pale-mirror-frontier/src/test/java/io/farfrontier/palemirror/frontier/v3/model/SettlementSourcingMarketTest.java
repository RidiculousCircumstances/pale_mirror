package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.supply.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class SettlementSourcingMarketTest {
    @Test void activeProfileSharesEconomicPlanAcrossProductionFundingProtectionAndRecovery() {
        var state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:economic-plan-boundaries"),
                20260918065L, FrontierRulesets.marketQuarryGraybox()));
        var codec = new io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec();
        var restored = codec.decode(codec.encode(state));
        assertEquals(state, restored);
        for (var participant : state.companies().goodsTrade().participants().participants().values()) {
            var view = GoodsParticipantView.read(state, participant);
            var plan = view.supplyPlan().orElseThrow(); var economy = plan.economy().orElseThrow();
            assertEquals(view, GoodsParticipantView.read(restored, restored.companies().goodsTrade().participants().participants().get(participant.party().id())));
            long funds = 0;
            for (var item : view.stocks().keySet()) {
                funds += GoodsParticipantFunding.purchase(view, state.bootstrap().ruleset().goodsTrade(), item).raw();
                assertEquals(view.stocks().get(item).protectedMinimum(), PublicStockProtection.minimum(state, participant.party().id(), item));
                var demand = ProductionStockDemand.read(state, participant.party().id(), item);
                assertEquals(Math.max(economy.productionTargets().get(item), plan.require(item).protectedInput()), demand.target());
            }
            assertTrue(funds <= view.availableMoney().raw(), "shared budgets must not spend a treasury twice");
            assertEquals(plan.reserves().get(SettlementFoodPolicy.BREAD).reorderPoint(), SettlementFoodPolicy.reserveRequirement(state, participant.party().id()));
        }
    }
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

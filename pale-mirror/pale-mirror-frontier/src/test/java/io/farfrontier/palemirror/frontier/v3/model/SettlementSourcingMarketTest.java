package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.supply.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class SettlementSourcingMarketTest {
    @Test void emptyDemandDrivenRosterDoesNotErasePotentialSourceCapacityOrGrantPermission() {
        var state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:potential-capacity"),
                20260918065L, FrontierRulesets.marketQuarryGraybox()));
        var home = new SubjectId("settlement:6");
        state = state.withStrategicPlans(state.strategicPlans().withWorkPermissions(home, ResidentWorkPermissions.none()));
        var stocks = new TreeMap<String, SupplyPlanner.Stock>();
        for (var commodity : state.bootstrap().ruleset().goodsTrade().policies().get(GoodsPolicyKind.PUBLIC_SETTLEMENT))
            stocks.put(commodity.itemKind(), new SupplyPlanner.Stock(0, 0, 0, 0, 0, 0, 1,
                    state.bootstrap().ruleset().goodsTrade().supplyPlanning().require(commodity.itemKind())));
        var processes = SupplyCapacityComposition.read(state, home, stocks);
        assertTrue(processes.stream().anyMatch(p -> p.product().equals("minecraft:coal")));
        // The genesis field has not been prepared; potential workers must not invent crops.
        assertFalse(processes.stream().anyMatch(p -> p.product().equals("minecraft:wheat")));
        assertTrue(processes.stream().anyMatch(p -> p.product().equals("minecraft:bread")));
        assertEquals(ResidentWorkPermissions.none(), SettlementWorkPolicy.permissions(state, home));
        for (var process : processes) for (var worker : process.labourCapacity().keySet())
            assertEquals(home, state.humanPopulation().resident(new SubjectId(worker)).settlementId());
    }
    @Test void personalProvisionsCannotCancelProductionForAnEmptyPublicStore() {
        var state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:incremental-production"),
                20260918065L, FrontierRulesets.marketQuarryGraybox()));
        var home = new SubjectId("settlement:6"); var actor = new SubjectId("resident:6-12");
        var ledger = state.inventory().fungibleResources();
        var store = ReferenceContainerCustody.scopeId(FrontierWorldState.depotId(home));
        var removed = new TreeMap<SubjectId, Integer>();
        for (var entry : ledger.accounts().get(store).lotQuantities().entrySet())
            if (ledger.lots().get(entry.getKey()).itemKind().equals("minecraft:bread")) removed.put(entry.getKey(), entry.getValue());
        ledger = ledger.transferToNewAccount(store, new CustodyAccount(new SubjectId("custody:test-withdrawn-food"),
                new ResourceCustody.Player(UUID.fromString("00000000-0000-0000-0000-000000000099")), removed, Map.of()));
        var lot = new SubjectId("lot:test-personal-food");
        ledger = ledger.issue(new ResourceLot(lot, home, "minecraft:bread", 20, "test:personal", List.of()),
                new CustodyAccount(new SubjectId("custody:test-personal-food"), new ResourceCustody.Actor(actor), Map.of(lot, 20), Map.of()));
        state = state.withInventory(state.inventory().withFungibleResources(ledger));
        var participant = state.companies().goodsTrade().participants().participants().get(home);
        var view = GoodsParticipantView.read(state, participant);
        assertEquals(0, view.stocks().get("minecraft:bread").unclaimedInStorage());
        long requested = view.supplyPlan().orElseThrow().economy().orElseThrow().additionalProduction().get("minecraft:bread");
        assertTrue(requested > 0);
        var demand = ProductionStockDemand.read(state, home, "minecraft:bread");
        assertEquals(20, demand.held());
        assertEquals(requested, demand.missing(), "private food cannot fulfill an incremental public production order");
        var codec = new io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec();
        assertEquals(demand, ProductionStockDemand.read(codec.decode(codec.encode(state)), home, "minecraft:bread"));
    }
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
                assertEquals(economy.additionalProduction().get(item), demand.missing());
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

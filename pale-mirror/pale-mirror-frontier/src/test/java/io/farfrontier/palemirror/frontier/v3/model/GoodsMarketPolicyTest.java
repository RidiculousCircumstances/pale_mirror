package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import io.farfrontier.palemirror.frontier.v3.process.GoodsParticipantProcess;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class GoodsMarketPolicyTest {
    @Test void immediateConsumptionFundingDoesNotLeakIntoInheritedIngredientDeadlines() {
        var state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:funding-alternative"),
                125L, FrontierRulesets.dynamicFarmingGraybox()));
        var rules = state.bootstrap().ruleset().goodsTrade();
        var participant = state.companies().goodsTrade().participants().participants().get(new SubjectId("settlement:1"));
        var original = GoodsParticipantView.read(state, participant);
        var zeros = new TreeMap<String, Long>(); var prices = new TreeMap<String, Long>();
        var demands = new TreeMap<String, io.farfrontier.palemirror.frontier.v3.model.supply.SupplyPlanner.Demand>();
        var stocks = new TreeMap<String, GoodsParticipantView.Stock>();
        for (var item : original.stocks().keySet()) {
            zeros.put(item, 0L); prices.put(item, 1L); stocks.put(item, new GoodsParticipantView.Stock(0, 0, 0));
            demands.put(item, new io.farfrontier.palemirror.frontier.v3.model.supply.SupplyPlanner.Demand(20, 0, 0, 0,
                    Set.of(io.farfrontier.palemirror.frontier.v3.model.supply.SupplyPlanner.Reason.CONSUMPTION_DEADLINE), Optional.empty()));
        }
        var budgets = new TreeMap<>(zeros); budgets.put("minecraft:coal", 100L);
        var economy = new io.farfrontier.palemirror.frontier.v3.model.economy.EconomicPlan(96000, zeros, zeros, budgets,
                0, 0, 0, List.of(), List.of(), Set.of(), prices, Set.of(), zeros);
        var plan = new io.farfrontier.palemirror.frontier.v3.model.supply.SupplyPlanner.Plan(demands, Map.of(), Map.of(), Optional.of(economy));
        var view = new GoodsParticipantView(participant, original.residents(), new FixedScalar(100), stocks, Map.of(), Optional.of(plan));
        assertEquals(20, GoodsParticipantFunding.purchase(view, rules, "minecraft:bread").raw());
        assertEquals(0, GoodsParticipantFunding.purchase(view, rules, "minecraft:wheat").raw());
    }
    @Test void priceDiscoveryUsesCurrentUncommittedFundedQuotesWithoutStartingTransport() {
        var state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:price-feedback"),
                20260918065L, FrontierRulesets.marketQuarryGraybox()));
        var trade = state.companies().goodsTrade();
        var seller = trade.participants().participants().get(new SubjectId("settlement:1"));
        var buyer = trade.participants().participants().get(new SubjectId("settlement:3"));
        var source = FungibleResourceCustodySupport.accountAtContainer(state, new SubjectId("container:1-depot")).orElseThrow();
        var lot = new SubjectId("lot:price-feedback");
        var staging = new SubjectId("custody:price-feedback");
        var resources = state.inventory().fungibleResources().issue(
                new ResourceLot(lot, seller.party().id(), "minecraft:coal", 8, "fixture:price-feedback", List.of()),
                new CustodyAccount(staging, source.custody(), Map.of(lot, 8), Map.of()));
        resources = resources.transfer(staging, source.id(), Map.of(lot, 8), Map.of());
        state = state.withInventory(state.inventory().withFungibleResources(resources));
        var quote = new GoodsTradeOrder(new SubjectId("order:funded-price-feedback"), buyer.party(), seller.party(),
                GoodsTradeOrder.Side.BUY, new SubjectId("container:3-depot"), "minecraft:coal", 2, 0, FixedScalar.whole(6), 24000);
        state = GoodsTradeStateSupport.place(state, buyer.party().id(), quote, 0);
        var market = GoodsMarketDemand.read(state, seller, "minecraft:coal", 8, 2400);
        assertEquals(FixedScalar.whole(6), market.buyers().getFirst().landedLimit());
        assertEquals(2, market.buyers().getFirst().quantity());
        var duplicate = new GoodsTradeOrder(new SubjectId("order:duplicate-price-feedback"), buyer.party(), seller.party(),
                quote.side(), quote.containerId(), quote.itemKind(), quote.quantity(), 0, quote.unitPriceLimit(), quote.expiresAtTick());
        state = GoodsTradeStateSupport.place(state, buyer.party().id(), duplicate, 0);
        assertEquals(1, GoodsMarketDemand.read(state, seller, "minecraft:coal", 8, 2400).buyers().size(),
                "repeated quotes must not duplicate demand or the buyer's purse");
        assertTrue(state.shipments().missions().isEmpty());
        assertTrue(GoodsMarketDemand.read(state, seller, "minecraft:coal", 8, 24000).buyers().isEmpty(), "expired consent cannot set prices");
        var ledger = state.inventory().economics();
        ledger = ledger.transfer(buyer.party().id(), seller.party().id(),
                ledger.availableToReserve(buyer.party().id()).minus(FixedScalar.whole(3)));
        state = state.withInventory(state.inventory().withEconomics(ledger));
        assertEquals(FixedScalar.whole(3), GoodsMarketDemand.read(state, seller, "minecraft:coal", 8, 2400).buyers().getFirst().budget(),
                "a retained quote cannot invent funds spent since publication");
        ledger = ledger.transfer(buyer.party().id(), seller.party().id(), FixedScalar.whole(3));
        state = state.withInventory(state.inventory().withEconomics(ledger));
        assertTrue(GoodsMarketDemand.read(state, seller, "minecraft:coal", 8, 2400).buyers().isEmpty());
        assertTrue(state.companies().goodsTrade().contracts().isEmpty(), "price advice cannot admit a sale");
    }
    @Test void aSmallBudgetKeepsInterestInCheaperPartialOffersWithoutInventingFunds() {
        // Explicit unmet production input, not an assumption that a stocked new
        // settlement must buy something under every economic policy.
        var state = BakeryPartialBatchTest.fixture(63, FrontierRulesets.marketQuarryGraybox());
        var participant = state.companies().goodsTrade().participants().participants().get(new SubjectId("settlement:1"));
        var view = GoodsParticipantView.read(state, participant);
        var small = new GoodsParticipantView(participant, view.residents(), new FixedScalar(1), view.stocks(), view.purchaseLimits(), view.supplyPlan());
        var intents = GoodsParticipantPolicies.require(participant).decide(small, state.bootstrap().ruleset().goodsTrade());
        var buy = intents.stream().filter(i -> i.side() == GoodsTradeOrder.Side.BUY).findFirst().orElseThrow();
        assertEquals(1, buy.limit().raw(), "unit ceiling cannot exceed the entire remaining budget");
        assertTrue(buy.quantity() > 0);
        var empty = new GoodsParticipantView(participant, view.residents(), FixedScalar.ZERO, view.stocks(), view.purchaseLimits(), view.supplyPlan());
        assertTrue(GoodsParticipantPolicies.require(participant).decide(empty, state.bootstrap().ruleset().goodsTrade()).stream()
                .noneMatch(i -> i.side() == GoodsTradeOrder.Side.BUY));
    }
    @Test void activeContractAndReservedStockCannotBeErasedAtPublication() {
        var state = GoodsTradeTest.reserved();
        var contract = state.companies().goodsTrade().contracts().values().iterator().next();
        var source = FungibleResourceCustodySupport.accountAtContainer(state, contract.sourceContainerId()).orElseThrow();
        var erased = state.withCompanies(state.companies().withGoodsTrade(GoodsTradeState.empty()));
        assertThrows(IllegalArgumentException.class, () -> FrontierReferenceClosure.validateTransition(state, erased, List.of()));
        var resources = state.inventory().fungibleResources().releaseClaim(source.id(), contract.outstandingClaims().keySet().iterator().next());
        var unreserved = state.withInventory(state.inventory().withFungibleResources(resources));
        assertThrows(IllegalArgumentException.class, () -> FrontierDomainRelationships.validate(unreserved));
    }
    @Test void buyerCeilingDoesNotAuthorizeAMidpointInsteadOfTheSellersAsk() {
        var trade = GoodsTradeTest.breadReserved().companies().goodsTrade();
        var contract = trade.contracts().values().iterator().next();
        var sell = trade.orders().get(contract.sellOrderId()); var originalBuy = trade.orders().get(contract.buyOrderId());
        var buy = new GoodsTradeOrder(originalBuy.id(), originalBuy.party(), originalBuy.counterparty(), originalBuy.side(),
                originalBuy.containerId(), originalBuy.itemKind(), originalBuy.quantity(), originalBuy.committedQuantity(),
                sell.unitPriceLimit().multiply(3), originalBuy.expiresAtTick());
        var midpoint = new GoodsTradeContract(contract.id(), contract.sellOrderId(), contract.buyOrderId(), contract.seller(),
                contract.buyer(), contract.fulfillment(), contract.sourceContainerId(), contract.receiverContainerId(), contract.itemKind(), contract.quantity(),
                sell.unitPriceLimit().multiply(2), contract.financialReservationId(), contract.revision(), contract.outstandingClaims(), contract.acceptances());
        assertDoesNotThrow(() -> GoodsTradeState.validateTerms(contract, sell, buy));
        assertThrows(IllegalArgumentException.class, () -> GoodsTradeState.validateTerms(midpoint, sell, buy));
    }
    @Test void onlyAcceptedGoodsEnterPriceHistoryAndRetirementRetainsTheirPrice() {
        var state = GoodsTradeTest.breadReserved();
        var trade = state.companies().goodsTrade();
        var contract = trade.contracts().values().iterator().next();
        assertTrue(trade.priceHistory().commodities().isEmpty(), "reservation is not a market sale");
        var claim = state.inventory().fungibleResources().claims().get(contract.outstandingClaims().keySet().iterator().next());
        var source = FungibleResourceCustodySupport.accountAtContainer(state, contract.sourceContainerId()).orElseThrow();
        var destination = FungibleResourceCustodySupport.accountAtContainer(state, contract.receiverContainerId()).orElseThrow();
        var resources = state.inventory().fungibleResources().transfer(source.id(), destination.id(),
                claim.lotQuantities(), Map.of(claim.id(), claim.quantity()));
        state = state.withInventory(state.inventory().withFungibleResources(resources));
        var splits = new HashMap<SubjectId, SubjectId>();
        claim.lotQuantities().forEach((lot, quantity) -> {
            if (resources.lots().get(lot).quantity() > quantity)
                splits.put(lot, new SubjectId("lot:market-test-bought/" + splits.size()));
        });
        var receipt = new GoodsTradeAcceptance(new SubjectId("receipt:market-history"), contract.id(), contract.revision(),
                new ResourceTitleTransfer(destination.id(), claim.id(), contract.seller().id(), contract.buyer().id(), claim.lotQuantities(), splits));
        state = GoodsTradeTest.factForFamily(state, contract.buyer().id(), new GoodsTradeAccepted(receipt), 100, "goods-trade");
        var accepted = state;
        assertThrows(IllegalArgumentException.class, () -> GoodsTradeTest.factForFamily(accepted, contract.buyer().id(),
                new GoodsTradeAccepted(receipt), 100, "goods-trade"));
        long expiry = Math.max(trade.orders().get(contract.sellOrderId()).expiresAtTick(), trade.orders().get(contract.buyOrderId()).expiresAtTick());
        state = GoodsTradeTest.factForFamily(state, GoodsTradeMarketIdentity.OWNER, new GoodsTradeRetired(
                Set.of(contract.sellOrderId(), contract.buyOrderId()), Set.of(contract.id())), expiry + 1, "goods-trade");
        assertTrue(state.companies().goodsTrade().contracts().isEmpty());
        var history = state.companies().goodsTrade().priceHistory();
        assertEquals(contract.quantity(), history.commodities().get(contract.itemKind()).getFirst().quantity());
        assertEquals(contract.goodsUnitPrice(), history.reference(contract.itemKind(), 24000).orElseThrow());
        assertEquals(state, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(state)));
    }
    @Test void ordinaryReviewPersistsCurrentPricesAndRejectsForgedOrRepeatedPolicyEvidence() {
        var state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:market-policy"),
                20260918065L, FrontierRulesets.marketQuarryGraybox()));
        var home = new SubjectId("settlement:1");
        var events = FrontierWorldRuntimeDefinition.planScheduled(state, GoodsParticipantProcess.review(home, 400), new SimInstant(400));
        var price = events.stream().map(ProposedEvent::payload).filter(GoodsParticipantPricesReviewed.class::isInstance)
                .map(GoodsParticipantPricesReviewed.class::cast).findFirst().orElseThrow();
        var before = state;
        assertThrows(IllegalArgumentException.class, () -> GoodsParticipantPricing.apply(before, home,
                new GoodsParticipantPricesReviewed(1, 400, price.prices()), 400));
        var historical = new HashMap<>(price.prices());
        var old = historical.get("minecraft:coal");
        historical.put("minecraft:coal", new io.farfrontier.palemirror.frontier.v3.model.market.MarketPricePolicy.Quote(
                old.ask().plus(FixedScalar.ONE), old.bid(), old.reference(), old.reviewedAt(), old.surplus(), old.lastSale()));
        var retained = new GoodsParticipantPricesReviewed(0, 400, historical);
        assertThrows(IllegalArgumentException.class, () -> GoodsParticipantPricing.apply(before, home, retained, 400),
                "new proposals must still use the current policy");
        assertEquals(historical, GoodsParticipantPricing.applyCommitted(before, home, retained, 400)
                .companies().goodsTrade().participants().participants().get(home).prices(),
                "a committed historical decision is not recalculated using a newer strategy");
        state = GoodsTradeTest.applyEvents(state, events, 400, "goods-trade");
        assertEquals(price.prices(), state.companies().goodsTrade().participants().participants().get(home).prices());
        var recovered = new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(state));
        assertEquals(state, recovered);
        assertThrows(IllegalArgumentException.class, () -> GoodsParticipantPricing.apply(recovered, home, price, 400));
    }
    @Test void withdrawingUnmatchedRemainderCannotChangeReservedGoodsMoneyOrAcceptedTerms() {
        var state = GoodsTradeTest.breadReserved();
        var trade = state.companies().goodsTrade(); var contract = trade.contracts().values().iterator().next();
        var order = trade.orders().get(contract.sellOrderId());
        // The ordinary fixture may have committed the complete offer. Add a separate
        // uncommitted quote to exercise cancellation without inventing a receipt.
        var quote = new GoodsTradeOrder(new SubjectId("order:withdrawable"), order.party(), order.counterparty(), order.side(),
                order.containerId(), order.itemKind(), 20, 0, order.unitPriceLimit(), order.expiresAtTick());
        state = GoodsTradeStateSupport.place(state, quote.party().id(), quote, 0);
        var cancelled = new GoodsTradeOrderWithdrawn(quote.id(), 0);
        var changed = GoodsTradeTest.factForFamily(state, quote.party().id(), cancelled, 1, "goods-trade");
        assertFalse(changed.companies().goodsTrade().orders().containsKey(quote.id()));
        assertEquals(state.inventory(), changed.inventory());
        assertEquals(trade.contracts(), changed.companies().goodsTrade().contracts());
        var before = state;
        assertThrows(IllegalArgumentException.class, () -> GoodsTradeTest.factForFamily(before, quote.counterparty().id(), cancelled, 1, "goods-trade"));
    }
    @Test void repricingPartialOrderRetainsItsCommittedTermsAcrossRecoveryAndSettlement() {
        var state = GoodsTradeTest.breadReserved();
        var trade = state.companies().goodsTrade();
        var contract = trade.contracts().values().iterator().next();
        var sell = trade.orders().get(contract.sellOrderId());
        var orders = new HashMap<>(trade.orders());
        orders.put(sell.id(), new GoodsTradeOrder(sell.id(), sell.party(), sell.counterparty(), sell.side(),
                sell.containerId(), sell.itemKind(), sell.quantity() + 20, sell.committedQuantity(), sell.unitPriceLimit(), sell.expiresAtTick()));
        trade = new GoodsTradeState(orders, trade.contracts(), trade.participants(), trade.priceHistory(), trade.demandHistory());
        state = state.withCompanies(state.companies().withGoodsTrade(trade));
        var originalInventory = state.inventory();
        state = GoodsTradeTest.factForFamily(state, sell.party().id(),
                new GoodsTradeOrderWithdrawn(sell.id(), sell.committedQuantity()), 1, "goods-trade");
        var replacement = new GoodsTradeOrder(new SubjectId("order:repriced-remainder"), sell.party(), sell.counterparty(), sell.side(),
                sell.containerId(), sell.itemKind(), 20, 0, sell.unitPriceLimit().multiply(3), sell.expiresAtTick());
        state = GoodsTradeStateSupport.place(state, sell.party().id(), replacement, 1);
        state = new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(state));
        assertEquals(originalInventory, state.inventory(), "repricing neither refunds escrow nor releases committed stock");
        assertEquals(contract, state.companies().goodsTrade().contracts().get(contract.id()));
        var retained = state.companies().goodsTrade().orders().get(sell.id());
        assertEquals(0, retained.availableQuantity());
        assertEquals(contract.goodsUnitPrice(), retained.unitPriceLimit());
        assertEquals(sell.unitPriceLimit().multiply(3), state.companies().goodsTrade().orders().get(replacement.id()).unitPriceLimit());
        var claim = state.inventory().fungibleResources().claims().get(contract.outstandingClaims().keySet().iterator().next());
        var source = FungibleResourceCustodySupport.accountAtContainer(state, contract.sourceContainerId()).orElseThrow();
        var destination = FungibleResourceCustodySupport.accountAtContainer(state, contract.receiverContainerId()).orElseThrow();
        var resources = state.inventory().fungibleResources().transfer(source.id(), destination.id(),
                claim.lotQuantities(), Map.of(claim.id(), claim.quantity()));
        state = state.withInventory(state.inventory().withFungibleResources(resources));
        var splits = new HashMap<SubjectId, SubjectId>();
        claim.lotQuantities().forEach((lot, quantity) -> {
            if (resources.lots().get(lot).quantity() > quantity)
                splits.put(lot, new SubjectId("lot:repriced-accepted/" + splits.size()));
        });
        var receipt = new GoodsTradeAcceptance(new SubjectId("receipt:repriced-accepted"), contract.id(), contract.revision(),
                new ResourceTitleTransfer(destination.id(), claim.id(), contract.seller().id(), contract.buyer().id(), claim.lotQuantities(), splits));
        var before = state.inventory().economics().require(contract.seller().id()).balance();
        state = GoodsTradeTest.factForFamily(state, contract.buyer().id(), new GoodsTradeAccepted(receipt), 2, "goods-trade");
        assertEquals(before.plus(contract.deliveredUnitPrice().multiply(claim.quantity())),
                state.inventory().economics().require(contract.seller().id()).balance(), "payment uses the old contract, not the new quote");
    }
}

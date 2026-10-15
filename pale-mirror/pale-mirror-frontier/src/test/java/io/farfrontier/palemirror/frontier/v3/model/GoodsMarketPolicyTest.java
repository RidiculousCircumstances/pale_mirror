package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import io.farfrontier.palemirror.frontier.v3.process.GoodsParticipantProcess;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class GoodsMarketPolicyTest {
    @Test void aSmallBudgetKeepsInterestInCheaperPartialOffersWithoutInventingFunds() {
        var state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:small-buy-budget"),
                20260918065L, FrontierRulesets.marketQuarryGraybox()));
        var participant = state.companies().goodsTrade().participants().participants().get(new SubjectId("settlement:9"));
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
        assertEquals(contract.deliveredUnitPrice(), history.reference(contract.itemKind(), 24000).orElseThrow());
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
}

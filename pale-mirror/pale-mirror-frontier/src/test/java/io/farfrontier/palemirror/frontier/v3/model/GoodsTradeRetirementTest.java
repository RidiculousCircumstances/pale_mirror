package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import io.farfrontier.palemirror.frontier.v3.process.GoodsParticipantProcess;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class GoodsTradeRetirementTest {
    @Test void terminalFillRetiresBeforeItsSharedUnexpiredOfferWithoutReopeningFilledQuantity() {
        var state = GoodsTradeTest.initial(); var sell = GoodsTradeTest.sellOrder();
        var shared = new GoodsTradeOrder(sell.id(), sell.party(), sell.side(), sell.containerId(), sell.itemKind(),
                90, 0, sell.unitPriceLimit(), sell.expiresAtTick());
        state = GoodsTradeTest.factForFamily(state, shared.party().id(), new GoodsTradeOrderPlaced(shared), 0, "goods-trade");
        var buy = GoodsTradeTest.buyOrder();
        state = GoodsTradeTest.factForFamily(state, buy.party().id(), new GoodsTradeOrderPlaced(buy), 0, "goods-trade");
        var reservation = GoodsTradeTest.reservation();
        state = GoodsTradeTest.factForFamily(state, shared.party().id(), reservation, 0, "goods-trade");
        var contract = reservation.contract(); var claim = contract.outstandingClaims().keySet().iterator().next();
        var live = state;
        var retirement = new GoodsTradeRetired(java.util.Set.of(), java.util.Set.of(contract.id()));
        assertThrows(IllegalArgumentException.class, () -> GoodsTradeTest.factForFamily(live, GoodsTradeMarketIdentity.OWNER,
                retirement, 1, "goods-trade"));
        state = GoodsTradeTest.factForFamily(state, shared.party().id(), new GoodsTradeCancelled(new GoodsTradeDisposition(
                new SubjectId("receipt:closed-shared-fill"), contract.id(), contract.revision(), claim, contract.quantity(),
                GoodsTradeDisposition.Reason.CANCELLED_BEFORE_LOADING)), 1, "goods-trade");
        var before = state;
        var after = GoodsTradeTest.factForFamily(state, GoodsTradeMarketIdentity.OWNER, retirement, 2, "goods-trade");
        assertDoesNotThrow(() -> FrontierWorldStateTransitionValidator.INSTANCE.validateTransition(before, after));
        assertEquals(before.inventory(), after.inventory());
        var offer = after.companies().goodsTrade().orders().get(shared.id());
        assertEquals(60, offer.retiredCommittedQuantity()); assertEquals(30, offer.availableQuantity());
        assertEquals(90, offer.commit(30).committedQuantity());
        assertThrows(IllegalArgumentException.class, () -> offer.commit(31));
        assertTrue(after.companies().goodsTrade().contracts().isEmpty());
        var codec = new FrontierWorldStateCodec(); assertEquals(after, codec.decode(codec.encode(after)));
    }

    @Test void expiredWithdrawnContractSettlesBeforeALaterReviewRetiresIt() {
        var state = GoodsTradeTest.breadReserved();
        var contract = state.companies().goodsTrade().contracts().values().iterator().next();
        var seller = contract.seller().id();
        var account = FungibleResourceCustodySupport.accountAtContainer(state, contract.sourceContainerId()).orElseThrow();
        var resources = state.inventory().fungibleResources().destroy(account.id(),
                Map.of(new SubjectId("lot:trade-bread"), SettlementFoodPolicy.reserveRequirement(state, seller, 0L)), Map.of());
        state = state.withInventory(state.inventory().withFungibleResources(resources));
        long now = 10_001;
        var action = GoodsParticipantProcess.review(seller, now);
        var events = FrontierWorldRuntimeDefinition.planScheduled(state, action, new SimInstant(now));
        assertTrue(events.stream().anyMatch(event -> event.payload() instanceof GoodsTradeCancelled));
        assertTrue(events.stream().noneMatch(event -> event.payload() instanceof GoodsTradeRetired retired
                && retired.contractIds().contains(contract.id())));
        var settled = GoodsTradeTest.applyEvents(state, events, now, "goods-trade");
        var before = state;
        assertDoesNotThrow(() -> FrontierWorldStateTransitionValidator.INSTANCE.validateTransition(before, settled));
        assertTrue(settled.companies().goodsTrade().contracts().get(contract.id()).terminal());
        var codec = new FrontierWorldStateCodec(); var recovered = codec.decode(codec.encode(settled));
        var next = GoodsParticipantProcess.review(seller, now + 1);
        var cleanup = FrontierWorldRuntimeDefinition.planScheduled(recovered, next, new SimInstant(now + 1));
        assertTrue(cleanup.stream().anyMatch(event -> event.payload() instanceof GoodsTradeRetired retired
                && retired.contractIds().contains(contract.id())));
        var retired = GoodsTradeTest.applyEvents(recovered, cleanup, now + 1, "goods-trade");
        assertDoesNotThrow(() -> FrontierWorldStateTransitionValidator.INSTANCE.validateTransition(recovered, retired));
        assertFalse(retired.companies().goodsTrade().contracts().containsKey(contract.id()));
    }
}

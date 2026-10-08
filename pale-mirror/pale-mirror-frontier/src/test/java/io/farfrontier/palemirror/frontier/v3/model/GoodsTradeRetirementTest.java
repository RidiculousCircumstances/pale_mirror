package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import io.farfrontier.palemirror.frontier.v3.process.GoodsParticipantProcess;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class GoodsTradeRetirementTest {
    @Test void expiredWithdrawnContractSettlesBeforeALaterReviewRetiresIt() {
        var state = GoodsTradeTest.breadReserved();
        var contract = state.companies().goodsTrade().contracts().values().iterator().next();
        var seller = contract.seller().id();
        var account = FungibleResourceCustodySupport.accountAtContainer(state, contract.sourceContainerId()).orElseThrow();
        var resources = state.inventory().fungibleResources().destroy(account.id(),
                Map.of(new SubjectId("lot:trade-bread"), SettlementFoodPolicy.reserveRequirement(state, seller)), Map.of());
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

package io.farfrontier.palemirror.frontier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import org.junit.jupiter.api.Test;

class FrontierMarketTest {
    @Test
    void clearsAllResourcesFromOneSnapshotAndMakesAReversiblePricedPhysicalLoad() {
        FrontierWorldState state = FrontierWorldFactory.create(FrontierProfile.GRAYBOX_10, 7_201L);
        FrontierRoute route = state.routes().iterator().next();
        FrontierSettlement seller = state.settlement(route.leftSettlementId()).orElseThrow();
        FrontierSettlement buyer = state.settlement(route.rightSettlementId()).orElseThrow();
        seller.addStock(FrontierResource.MEDICINE, 200);
        buyer.removeStock(FrontierResource.MEDICINE, buyer.stock(FrontierResource.MEDICINE));
        state.advanceDay();
        ArrayList<FrontierEvent> events = new ArrayList<>();

        FrontierMarket.clear(state, "test:market", events);

        FrontierCargo load = state.cargoForRoute(route.id()).orElseThrow();
        assertEquals(FrontierResource.MEDICINE, load.resource(),
                "a non-food shortage must use the same canonical market instead of the removed food-only branch");
        assertTrue(load.creditValue() > load.amount(), "medicine's priced mutual-credit value must not collapse to its physical count");
        assertTrue(seller.netCredit() >= load.creditValue());
        assertTrue(buyer.netCredit() <= -load.creditValue());
        assertTrue(events.stream().anyMatch(value -> value.type() == FrontierEvent.Type.CARGO_DISPATCHED));
    }

    @Test
    void capacityAndCreditAreReservedBeforeCommitSoOneRouteCannotEmitConflictingLoads() {
        FrontierWorldState state = FrontierWorldFactory.create(FrontierProfile.GRAYBOX_10, 7_202L);
        FrontierRoute route = state.routes().iterator().next();
        FrontierSettlement seller = state.settlement(route.leftSettlementId()).orElseThrow();
        FrontierSettlement buyer = state.settlement(route.rightSettlementId()).orElseThrow();
        for (FrontierResource resource : FrontierResource.values()) {
            seller.addStock(resource, 1_000);
            buyer.removeStock(resource, buyer.stock(resource));
        }
        state.advanceDay();
        FrontierMarket.clear(state, "test:reservation", new ArrayList<>());

        assertEquals(1, state.cargo().stream().filter(value -> value.routeId().equals(route.id())).count(),
                "the visible route may carry one bounded physical load, not overlapping secret matches");
        assertFalse(state.cargo().stream().anyMatch(value -> value.amount() > route.capacity()));
        assertEquals(0, state.settlements().stream().mapToLong(FrontierSettlement::netCredit).sum());
    }
}

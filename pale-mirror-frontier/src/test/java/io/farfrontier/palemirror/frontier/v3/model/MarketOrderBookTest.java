package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarketOrderBookTest {
    @Test
    void acceptsOneCheapestCurrentQuoteThenPersistsItsExactCommercialCause() {
        SubjectId buyer = new SubjectId("settlement:1"); SubjectId seller = new SubjectId("company:1-works");
        MarketDemand demand = new MarketDemand(new SubjectId("demand:northwatch-bread-1"), buyer, new SubjectId("task:northwatch-bread-1"),
                "minecraft:bread", 64, FixedScalar.whole(3L), 100L, 200L, MarketDemandStatus.OPEN);
        CompanyQuote expensive = new CompanyQuote(new SubjectId("quote:northwatch-bread-expensive"), demand.id(), seller, 64,
                FixedScalar.whole(3L), 110L, 180L);
        CompanyQuote best = new CompanyQuote(new SubjectId("quote:northwatch-bread-best"), demand.id(), seller, 64,
                FixedScalar.whole(2L), 110L, 180L);
        MarketOrderBook quoted = MarketOrderBook.empty().open(demand).publish(expensive, 120L).publish(best, 120L);

        assertEquals(best, quoted.bestCurrentQuote(demand.id(), 120L).orElseThrow());
        MarketWorkOrder order = new MarketWorkOrder(new SubjectId("order:northwatch-bread-1"), demand.id(), best.id(), seller,
                demand.reasonId(), new SubjectId("reservation:northwatch-bread-1"), best.totalPrice(), MarketWorkOrderStatus.ACCEPTED);
        MarketOrderBook accepted = quoted.accept(order, 120L);

        assertEquals(MarketDemandStatus.ORDERED, accepted.demands().get(demand.id()).status());
        assertEquals(order, accepted.workOrders().get(order.id()));
        assertThrows(IllegalArgumentException.class, () -> accepted.accept(new MarketWorkOrder(new SubjectId("order:northwatch-bread-duplicate"),
                demand.id(), expensive.id(), seller, demand.reasonId(), new SubjectId("reservation:northwatch-bread-duplicate"), expensive.totalPrice(),
                MarketWorkOrderStatus.ACCEPTED), 121L));
        assertEquals(MarketDemandStatus.FULFILLED, accepted.complete(order.id()).demands().get(demand.id()).status());
    }

    @Test
    void rejectsStaleAndOverBudgetOffersThenRoundTripsTheBoundedBookInWorldState() {
        SubjectId buyer = new SubjectId("settlement:1"); SubjectId seller = CompanyFoundationProcess.companyId(buyer);
        MarketDemand demand = new MarketDemand(new SubjectId("demand:codec-bread-1"), buyer, new SubjectId("task:codec-bread-1"),
                "minecraft:bread", 64, FixedScalar.whole(2L), 100L, 150L, MarketDemandStatus.OPEN);
        assertThrows(IllegalArgumentException.class, () -> MarketOrderBook.empty().open(demand).publish(new CompanyQuote(new SubjectId("quote:codec-overbudget"),
                demand.id(), seller, 64, FixedScalar.whole(3L), 110L, 140L), 120L));
        assertThrows(IllegalArgumentException.class, () -> MarketOrderBook.empty().open(demand).publish(new CompanyQuote(new SubjectId("quote:codec-stale"),
                demand.id(), seller, 64, FixedScalar.whole(2L), 110L, 140L), 151L));

        MarketOrderBook book = MarketOrderBook.empty().open(demand).expireOpen(151L);
        assertEquals(MarketDemandStatus.EXPIRED, book.demands().get(demand.id()).status());
        FrontierWorldState state = foundedState();
        FrontierWorldState persisted = state.withCompanies(state.companies().withMarket(book));
        assertEquals(persisted, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(persisted)));
        assertTrue(persisted.companies().market().demands().containsKey(demand.id()));
    }

    private static FrontierWorldState foundedState() {
        FrontierWorldState state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:market-book"), 88L));
        for (Settlement settlement : state.bootstrap().settlements()) {
            for (var event : CompanyFoundationProcess.plan(state, CompanyFoundationProcess.review(settlement.id(), 1, 4_000L))) {
                if (event.payload() instanceof CompanyRegistered registered) state = CompanyFoundationProcess.reduce(state, settlement.id(), registered);
                if (event.payload() instanceof EmploymentContractOpened opened) state = CompanyFoundationProcess.reduceEmployment(state, settlement.id(), opened);
            }
        }
        return state;
    }
}

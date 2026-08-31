package io.farfrontier.palemirror.frontier.v3.model;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;

import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import org.junit.jupiter.api.Test;

import java.util.Set;

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
                demand.reasonId(), new SubjectId("job:northwatch-bread-1"), new SubjectId("reservation:northwatch-bread-1"), best.totalPrice(), MarketWorkOrderStatus.ACCEPTED);
        MarketOrderBook accepted = quoted.accept(order, 120L);

        assertEquals(new MarketDemandOpened(demand), FrontierWorldRuntimeDefinition.payloadCodecs().decode("frontier.market_demand_opened",
                FrontierWorldRuntimeDefinition.payloadCodecs().encode(new MarketDemandOpened(demand))));
        assertEquals(new MarketQuotePublished(best), FrontierWorldRuntimeDefinition.payloadCodecs().decode("frontier.market_quote_published",
                FrontierWorldRuntimeDefinition.payloadCodecs().encode(new MarketQuotePublished(best))));
        assertEquals(new MarketWorkOrderAccepted(order), FrontierWorldRuntimeDefinition.payloadCodecs().decode("frontier.market_work_order_accepted",
                FrontierWorldRuntimeDefinition.payloadCodecs().encode(new MarketWorkOrderAccepted(order))));
        MarketWorkOrderCancelled cancellation = new MarketWorkOrderCancelled(order.id(), order.jobId(), ProductionBlockReason.FACILITY_UNAVAILABLE);
        assertEquals(cancellation, FrontierWorldRuntimeDefinition.payloadCodecs().decode(cancellation.type(),
                FrontierWorldRuntimeDefinition.payloadCodecs().encode(cancellation)));
        assertEquals(MarketDemandStatus.ORDERED, accepted.demands().get(demand.id()).status());
        assertEquals(order, accepted.workOrders().get(order.id()));
        assertThrows(IllegalArgumentException.class, () -> accepted.accept(new MarketWorkOrder(new SubjectId("order:northwatch-bread-duplicate"),
                demand.id(), expensive.id(), seller, demand.reasonId(), new SubjectId("job:northwatch-bread-duplicate"), new SubjectId("reservation:northwatch-bread-duplicate"), expensive.totalPrice(),
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
        MarketDemandCancelled cancelled = new MarketDemandCancelled(demand.id(), MarketDemandCancellationReason.PRODUCTION_BLOCKED);
        assertEquals(cancelled, FrontierWorldRuntimeDefinition.payloadCodecs().decode(cancelled.type(),
                FrontierWorldRuntimeDefinition.payloadCodecs().encode(cancelled)));
        MarketOrderBook cancelledBook = MarketOrderBook.empty().open(demand).cancelOpen(demand.id());
        assertEquals(MarketDemandStatus.CANCELLED, cancelledBook.demands().get(demand.id()).status());
        assertThrows(IllegalArgumentException.class, () -> cancelledBook.cancelOpen(demand.id()));
        FrontierWorldState state = foundedState();
        FrontierWorldState persisted = state.withCompanies(state.companies().withMarket(book));
        assertEquals(persisted, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(persisted)));
        assertTrue(persisted.companies().market().demands().containsKey(demand.id()));
    }

    @Test
    void compactsOnlyOldTerminalDemandHistoryBeforeTheBoundedBookCanFill() {
        MarketOrderBook book = MarketOrderBook.empty(); SubjectId buyer = new SubjectId("settlement:1");
        for (int index = 0; index < 300; index++) {
            MarketDemand demand = new MarketDemand(new SubjectId("demand:retention-" + index), buyer, new SubjectId("task:retention-" + index),
                    "minecraft:bread", 64, FixedScalar.ONE, index, index, MarketDemandStatus.OPEN);
            book = book.open(demand).expireOpen(index + 1L);
        }
        MarketOrderBook compacted = book.compactTerminal(Set.of());
        assertEquals(MarketOrderBook.RETAINED_TERMINAL_DEMANDS, compacted.demands().size());
        assertTrue(!compacted.demands().containsKey(new SubjectId("demand:retention-0")));
        assertTrue(compacted.demands().containsKey(new SubjectId("demand:retention-299")));
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

package io.farfrontier.palemirror.frontier.v3.model.market;

import io.farfrontier.palemirror.frontier.v3.api.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class MarketPricingTest {
    @Test void purchaseConsentUsesFullTargetWithDeliveryAndAlternativeWithoutPercentageSteps() {
        var policy = new MarketPricePolicy(); var rules = MarketPricingRules.adaptive();
        var reference = new FixedScalar(16000);
        var previous = new MarketPricePolicy.Quote(reference, new FixedScalar(32000), reference, 0, 0, Optional.empty());
        var acquisition = new AcquisitionQuote(new FixedScalar(13600), new FixedScalar(23507));
        var context = new MarketPricePolicy.Context(FixedScalar.ZERO, Optional.empty(), 1000,
                SellerPriceDiscovery.Market.empty(), Optional.of(acquisition));
        var quote = policy.review(reference, reference, reference, 0, Optional.empty(), Optional.of(previous), rules.reviewTicks(), rules, context);
        assertEquals(new FixedScalar(32000).plus(acquisition.delivery()), quote.bid());
        assertEquals(reference, quote.reference()); assertEquals(reference, quote.ask());
        var ceiling = new FixedScalar(30000);
        var alternative = new MarketPricePolicy.Context(FixedScalar.ZERO, Optional.of(ceiling), 1000,
                SellerPriceDiscovery.Market.empty(), Optional.of(acquisition));
        assertEquals(ceiling, policy.review(reference, reference, reference, 0, Optional.empty(), Optional.of(previous),
                rules.reviewTicks(), rules, alternative).bid());
        var excessiveAsk = new MarketPricePolicy.Context(FixedScalar.ZERO, Optional.empty(), 1000,
                SellerPriceDiscovery.Market.empty(), Optional.of(new AcquisitionQuote(FixedScalar.whole(10), acquisition.delivery())));
        assertEquals(quote.bid(), policy.review(reference, reference, reference, 0, Optional.empty(), Optional.of(previous),
                rules.reviewTicks(), rules, excessiveAsk).bid(), "an arbitrary supplier ask is not buyer willingness");
    }
    @Test void calculatedAskCanRiseOrFallImmediatelyWithoutAMaterialFloorForExistingStock() {
        var policy = new MarketPricePolicy(); var rules = MarketPricingRules.adaptive();
        var quote = policy.review(FixedScalar.whole(20), FixedScalar.whole(30), FixedScalar.whole(20), 10,
                Optional.empty(), Optional.empty(), 0, rules);
        var context = new MarketPricePolicy.Context(FixedScalar.whole(19), Optional.empty(), 0,
                market(demand(1, 10, 100, 1)), Optional.empty());
        quote = policy.review(FixedScalar.whole(20), FixedScalar.whole(30), FixedScalar.whole(20), 10,
                Optional.empty(), Optional.of(quote), 2400, rules, context);
        assertEquals(FixedScalar.ONE, quote.ask());
        var increased = new MarketPricePolicy.Context(FixedScalar.ZERO, Optional.empty(), 0,
                market(demand(1, 10, 100, 6)), Optional.empty());
        quote = policy.review(FixedScalar.whole(20), FixedScalar.whole(30), FixedScalar.whole(20), 10,
                Optional.empty(), Optional.of(quote), 4800, rules, increased);
        assertEquals(FixedScalar.whole(6), quote.ask());
        assertTrue(quote.lastSale().isEmpty(), "a quote is not a completed sale");
    }
    @Test void priceChoosesQuantityAndCompetitionRatherThanTheHighestBid() {
        var market = market(demand(1, 1, 10, 10), demand(2, 100, 200, 2));
        assertEquals(FixedScalar.whole(2), SellerPriceDiscovery.ask(FixedScalar.ONE, 101, market));
        var competitor = new SellerPriceDiscovery.Offer(new SubjectId("settlement:rival"), 100, FixedScalar.whole(3));
        var buyer = new SellerPriceDiscovery.Demand(new SubjectId("settlement:1"), 10, FixedScalar.whole(100),
                FixedScalar.whole(10), FixedScalar.ONE, List.of(competitor));
        assertEquals(new FixedScalar(1_999_999), SellerPriceDiscovery.ask(FixedScalar.whole(9), 10, market(buyer)),
                "goods price competes against landed cost, not against the buyer ceiling alone");
        assertEquals(FixedScalar.whole(10), SellerPriceDiscovery.ask(FixedScalar.ONE, 10, market(demand(1, 10, 100, 10))),
                "without extra volume, a lower price only reduces proceeds");
    }
    @Test void emptyInterestCompletedReceiptsAndElapsedTimeDoNotManufactureDiscountsOrMarkups() {
        var policy = new MarketPricePolicy(); var rules = MarketPricingRules.adaptive();
        var quote = policy.review(FixedScalar.whole(2), FixedScalar.whole(3), FixedScalar.whole(2), 64,
                Optional.empty(), Optional.empty(), 0, rules);
        for (long tick = 2400; tick <= 96000; tick += 2400)
            quote = policy.review(FixedScalar.whole(2), FixedScalar.whole(3), FixedScalar.whole(2), 128,
                    Optional.of(new SubjectId("receipt:sold")), Optional.of(quote), tick, rules);
        assertEquals(FixedScalar.whole(2), quote.ask());
        assertEquals(quote, policy.review(FixedScalar.whole(2), FixedScalar.whole(3), FixedScalar.whole(8), 128,
                quote.lastSale(), Optional.of(quote), 96001, rules));
    }
    @Test void budgetStockAndAlternativeWarehouseIdentityAreFinite() {
        assertEquals(FixedScalar.ONE, SellerPriceDiscovery.ask(FixedScalar.ONE, 100, market(demand(1, 100, 3, 10))),
                "equal revenue retains the current quote; the buyer cannot spend ten when it owns three");
        var same = demand(1, 100, 3, 10);
        assertThrows(IllegalArgumentException.class, () -> market(same, same));
        assertEquals(FixedScalar.whole(10), SellerPriceDiscovery.ask(FixedScalar.ONE, 1, market(demand(1, 100, 100, 10))));
        assertEquals(FixedScalar.ONE, SellerPriceDiscovery.ask(FixedScalar.ONE, 0, market(demand(1, 100, 100, 10))));
    }
    @Test void completedVolumeAndRecencySetReferenceWithoutTimeOnlyDriftOrDuplicateSales() {
        var first = sale(1, 100, 1, 10);
        var history = MarketPriceHistory.empty().record("grain", first).record("grain", sale(2, 100, 9, 2));
        assertEquals(new FixedScalar(2_800_000), history.reference("grain", 1000).orElseThrow());
        var before = history;
        assertThrows(IllegalArgumentException.class, () -> before.record("grain", first));
        history = history.record("grain", sale(3, 1100, 10, 4));
        assertTrue(history.reference("grain", 1000).orElseThrow().raw() > 3_900_000);
        for (int i = 4; i <= 60; i++) history = history.record("grain", sale(i, 1100 + i, 1, 4));
        assertEquals(MarketPriceHistory.MAX_SALES, history.commodities().get("grain").size());
    }
    private static SellerPriceDiscovery.Market market(SellerPriceDiscovery.Demand... buyers) {
        return new SellerPriceDiscovery.Market(List.of(buyers));
    }
    private static SellerPriceDiscovery.Demand demand(int buyer, int quantity, long budget, long limit) {
        return new SellerPriceDiscovery.Demand(new SubjectId("settlement:" + buyer), quantity, FixedScalar.whole(budget),
                FixedScalar.whole(limit), FixedScalar.ZERO, List.of());
    }
    private static MarketPriceHistory.Sale sale(int n, long tick, int count, long price) {
        return new MarketPriceHistory.Sale(new SubjectId("receipt:" + n), new SubjectId("settlement:1"),
                new SubjectId("settlement:2"), tick, count, FixedScalar.whole(price));
    }
}

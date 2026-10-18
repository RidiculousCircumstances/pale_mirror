package io.farfrontier.palemirror.frontier.v3.model.market;

import io.farfrontier.palemirror.frontier.v3.api.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class MarketPricingTest {
    @Test void purchaseConsentIncludesDeliveryWithoutInflatingGoodsReferenceOrSellerAsk() {
        var policy = new MarketPricePolicy(); var rules = MarketPricingRules.adaptive();
        var reference = new FixedScalar(16000);
        var previous = new MarketPricePolicy.Quote(reference, new FixedScalar(32000), reference, 0, 0, Optional.empty(), 0);
        var acquisition = new AcquisitionQuote(new FixedScalar(13600), new FixedScalar(23507));
        var context = new MarketPricePolicy.Context(FixedScalar.ZERO, Optional.empty(), 1000, false,
                Optional.empty(), Optional.of(acquisition));
        var quote = policy.review(reference, reference, reference, 0, Optional.empty(), Optional.of(previous), rules.reviewTicks(), rules, context);
        assertEquals(acquisition.total(), quote.bid(), "known freight must not wait for goods-price discovery to catch up");
        assertEquals(reference, quote.reference());
        assertEquals(reference, quote.ask());
        var ceiling = new FixedScalar(30000);
        var alternative = new MarketPricePolicy.Context(FixedScalar.ZERO, Optional.of(ceiling), 1000, false,
                Optional.empty(), Optional.of(acquisition));
        assertEquals(ceiling, policy.review(reference, reference, reference, 0, Optional.empty(), Optional.of(previous),
                rules.reviewTicks(), rules, alternative).bid());
        var excessiveAsk = new MarketPricePolicy.Context(FixedScalar.ZERO, Optional.empty(), 1000, false,
                Optional.empty(), Optional.of(new AcquisitionQuote(FixedScalar.whole(10), acquisition.delivery())));
        assertEquals(new FixedScalar(32000).plus(acquisition.delivery()), policy.review(reference, reference, reference, 0,
                Optional.empty(), Optional.of(previous), rules.reviewTicks(), rules, excessiveAsk).bid(),
                "an arbitrary seller ask cannot replace the buyer's willingness for goods");
    }
    @Test void adaptivePricesCanLiquidateBelowSeedAndPastMaterialCostWithoutInventingSales() {
        var policy = new MarketPricePolicy(); var rules = MarketPricingRules.adaptive();
        var context = new MarketPricePolicy.Context(FixedScalar.whole(19), Optional.empty(), 0, false,
                Optional.of(FixedScalar.ONE));
        var quote = policy.review(FixedScalar.whole(20), FixedScalar.whole(30), FixedScalar.whole(20), 10,
                Optional.empty(), Optional.empty(), 0, rules, context);
        for (long tick = rules.reviewTicks(); tick <= 80 * rules.reviewTicks(); tick += rules.reviewTicks())
            quote = policy.review(FixedScalar.whole(20), FixedScalar.whole(30), FixedScalar.whole(20), 10,
                    Optional.empty(), Optional.of(quote), tick, rules, context);
        assertEquals(FixedScalar.ONE, quote.ask());
        assertTrue(quote.lastSale().isEmpty());
        assertEquals(0, quote.unsoldOfferedTicks());
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
    @Test void quotationUsesRealSignalsAndBoundedCadenceInsteadOfRouteWaitingOrRepeatedWakeups() {
        var policy = new MarketPricePolicy(); var rules = MarketPricingRules.initial();
        var initial = policy.review(FixedScalar.whole(2), FixedScalar.whole(3), FixedScalar.whole(2), 64,
                Optional.empty(), Optional.empty(), 0, rules);
        var waiting = policy.review(FixedScalar.whole(2), FixedScalar.whole(3), FixedScalar.whole(2), 64,
                Optional.empty(), Optional.of(initial), 2400, rules);
        assertEquals(initial.ask(), waiting.ask()); assertEquals(initial.bid(), waiting.bid());
        var sold = policy.review(FixedScalar.whole(2), FixedScalar.whole(3), FixedScalar.whole(2), 32,
                Optional.of(new SubjectId("receipt:sold")), Optional.of(waiting), 4800, rules);
        assertEquals(new FixedScalar(2_100_000), sold.ask());
        assertEquals(sold, policy.review(FixedScalar.whole(2), FixedScalar.whole(3), FixedScalar.whole(2), 128,
                sold.lastSale(), Optional.of(sold), 4801, rules));
        var accumulated = policy.review(FixedScalar.whole(2), FixedScalar.whole(3), FixedScalar.whole(2), 128,
                sold.lastSale(), Optional.of(sold), 7200, rules);
        assertEquals(new FixedScalar(1_995_000), accumulated.ask());
    }
    @Test void fundedPriceGapCanClearWithoutRequiringAPreexistingCaravan() {
        var policy = new MarketPricePolicy(); var rules = MarketPricingRules.initial();
        var quote = policy.review(FixedScalar.whole(20), FixedScalar.whole(30), FixedScalar.whole(20), 10,
                Optional.empty(), Optional.empty(), 0, rules);
        var gap = new MarketPricePolicy.Context(FixedScalar.ZERO, Optional.empty(), 0, false, Optional.of(FixedScalar.whole(6)));
        var initial = quote;
        for (long now = rules.reviewTicks(); now <= 30 * rules.reviewTicks(); now += rules.reviewTicks())
            quote = policy.review(FixedScalar.whole(20), FixedScalar.whole(30), FixedScalar.whole(20), 10,
                    Optional.empty(), Optional.of(quote), now, rules, gap);
        assertEquals(FixedScalar.whole(6), quote.ask(), "constant surplus and no journey must not lock out a funded lower bid");
        assertEquals(0, quote.unsoldOfferedTicks(), "a funded bid alone is not a standing sell offer");
        assertEquals(initial, policy.review(FixedScalar.whole(20), FixedScalar.whole(30), FixedScalar.whole(20), 10,
                Optional.empty(), Optional.of(initial), 1, rules, gap));
        var compatible = new MarketPricePolicy.Context(FixedScalar.ZERO, Optional.empty(), 0, false, Optional.of(FixedScalar.whole(21)));
        assertEquals(initial.ask(), policy.review(FixedScalar.whole(20), FixedScalar.whole(30), FixedScalar.whole(20), 10,
                Optional.empty(), Optional.of(initial), 2400, rules, compatible).ask());
        var floor = new MarketPricePolicy.Context(FixedScalar.whole(19), Optional.empty(), 0, false, Optional.of(FixedScalar.ONE));
        assertEquals(FixedScalar.whole(19), policy.review(FixedScalar.whole(20), FixedScalar.whole(30), FixedScalar.whole(20), 10,
                Optional.empty(), Optional.of(initial), 2400, rules, floor).ask());
    }
    @Test void losingStandingOfferAgesEvenWhenBuyerCouldAffordItAndNoCaravanExists() {
        var policy = new MarketPricePolicy(); var rules = MarketPricingRules.adaptive();
        var context = new MarketPricePolicy.Context(FixedScalar.ZERO, Optional.empty(), 0, true,
                Optional.of(FixedScalar.whole(11)));
        var quote = policy.review(FixedScalar.whole(6), FixedScalar.ONE, FixedScalar.whole(6), 37,
                Optional.empty(), Optional.empty(), 0, rules, context);
        for (long tick = rules.reviewTicks(); tick <= rules.recencyTicks(); tick += rules.reviewTicks())
            quote = policy.review(FixedScalar.whole(6), FixedScalar.ONE, FixedScalar.whole(6), 37,
                    Optional.empty(), Optional.of(quote), tick, rules, context);
        assertEquals(new FixedScalar(5_700_000), quote.ask());
        assertEquals(rules.recencyTicks(), quote.unsoldOfferedTicks());
        assertTrue(quote.lastSale().isEmpty());
    }
    private static MarketPriceHistory.Sale sale(int n, long tick, int count, long price) {
        return new MarketPriceHistory.Sale(new SubjectId("receipt:" + n), new SubjectId("settlement:1"),
                new SubjectId("settlement:2"), tick, count, FixedScalar.whole(price));
    }
}

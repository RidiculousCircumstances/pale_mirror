package io.farfrontier.palemirror.frontier.v3.model.market;

import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ProductionValuationTest {
    @Test void valuesRealRecipeRatiosAndAmortizedFuelWithoutACommodityBranch() {
        var prices = Map.of("grain", FixedScalar.ONE);
        var fuel = List.of(new ProductionValuation.Fuel(FixedScalar.whole(2), 1600));
        assertEquals(new FixedScalar(3_100_000),
                ProductionValuation.unitCost(Map.of("grain", 3), 1, 80, prices, fuel).orElseThrow());
        assertEquals(new FixedScalar(3_100_000),
                ProductionValuation.unitCost(Map.of("grain", 60), 20, 1600, prices, fuel).orElseThrow());
        assertTrue(ProductionValuation.unitCost(Map.of("missing-input", 3), 1, 80, prices, fuel).isEmpty());
        assertTrue(ProductionValuation.unitCost(Map.of("grain", 3), 1, 80, prices, List.of()).isEmpty());
    }
    @Test void constrainedQuotesRespectMaterialCostAlternativeAndPositiveUnsoldAccessEvidence() {
        var policy = new MarketPricePolicy(); var rules = MarketPricingRules.initial();
        var accessible = new MarketPricePolicy.Context(FixedScalar.whole(3), Optional.of(FixedScalar.whole(4)), 1000, true);
        var quote = policy.review(FixedScalar.whole(4), FixedScalar.whole(6), FixedScalar.whole(4), 64,
                Optional.empty(), Optional.empty(), 0, rules, accessible);
        assertEquals(FixedScalar.whole(4), quote.bid());
        for (long tick = 2400; tick <= 48000; tick += 2400)
            quote = policy.review(FixedScalar.whole(4), FixedScalar.whole(6), FixedScalar.whole(4), 64,
                    Optional.empty(), Optional.of(quote), tick, rules, accessible);
        assertEquals(FixedScalar.whole(3), quote.ask()); assertEquals(FixedScalar.whole(4), quote.bid());
        var pending = new MarketPricePolicy.Context(FixedScalar.whole(3), Optional.empty(), 0, false);
        var unchanged = policy.review(FixedScalar.whole(4), FixedScalar.whole(6), quote.reference(), 64,
                Optional.empty(), Optional.of(quote), 50400, rules, pending);
        assertEquals(quote.ask(), unchanged.ask()); assertEquals(0, unchanged.unsoldAccessibleTicks());
    }
}

package io.farfrontier.palemirror.frontier.v3.model.market;

import io.farfrontier.palemirror.frontier.v3.api.*;
import java.math.BigInteger;
import java.util.*;

/** Participant quotation, separate from market clearing and public needs planning. */
public final class MarketPricePolicy {
    public record Quote(FixedScalar ask, FixedScalar bid, FixedScalar reference, long reviewedAt,
                        long surplus, Optional<SubjectId> lastSale, StockSalePlan salePlan) {
        public Quote {
            Objects.requireNonNull(salePlan); Objects.requireNonNull(ask); Objects.requireNonNull(bid); Objects.requireNonNull(reference);
            lastSale = Objects.requireNonNull(lastSale);
            if (ask.raw() <= 0 || bid.raw() <= 0 || reference.raw() <= 0 || reviewedAt < 0 || surplus < 0)
                throw new IllegalArgumentException("invalid retained quotation policy");
            if (salePlan.kind() == StockSalePlan.Kind.OPERATING_RECOVERY
                    && (salePlan.quantityLimit() > surplus || Math.multiplyExact(salePlan.quantityLimit(), ask.raw()) < salePlan.cashRequirement()))
                throw new IllegalArgumentException("stock recovery cannot finance its declared operating shortfall");
        }
    }
    public record Context(FixedScalar materialFloor, Optional<FixedScalar> alternativeCeiling,
                          int shortagePermille, SellerPriceDiscovery.Market market,
                          Optional<AcquisitionQuote> acquisition, Optional<ResourceEconomicValue> economics, long recoveryCash) {
        public Context(FixedScalar materialFloor, Optional<FixedScalar> alternativeCeiling, int shortagePermille, SellerPriceDiscovery.Market market, Optional<AcquisitionQuote> acquisition, Optional<ResourceEconomicValue> economics) {
            this(materialFloor, alternativeCeiling, shortagePermille, market, acquisition, economics, 0);
        }
        public Context(FixedScalar materialFloor, Optional<FixedScalar> alternativeCeiling, int shortagePermille,
                       SellerPriceDiscovery.Market market, Optional<AcquisitionQuote> acquisition) {
            this(materialFloor, alternativeCeiling, shortagePermille, market, acquisition, Optional.empty());
        }
        public Context {
            Objects.requireNonNull(materialFloor); Objects.requireNonNull(alternativeCeiling);
            Objects.requireNonNull(market); Objects.requireNonNull(acquisition); Objects.requireNonNull(economics);
            if (recoveryCash < 0 || materialFloor.raw() < 0 || alternativeCeiling.filter(v -> v.raw() <= 0).isPresent()
                    || shortagePermille < 0 || shortagePermille > 1000)
                throw new IllegalArgumentException("invalid economic price constraints");
        }
        public static Context empty() {
            return new Context(FixedScalar.ZERO, Optional.empty(), 0, SellerPriceDiscovery.Market.empty(), Optional.empty());
        }
    }
    public Quote review(FixedScalar seedAsk, FixedScalar seedBid, FixedScalar reference, long surplus,
                        Optional<SubjectId> lastSale, Optional<Quote> previous, long now, MarketPricingRules rules) {
        return review(seedAsk, seedBid, reference, surplus, lastSale, previous, now, rules, Context.empty());
    }
    public Quote review(FixedScalar seedAsk, FixedScalar seedBid, FixedScalar reference, long surplus,
                        Optional<SubjectId> lastSale, Optional<Quote> previous, long now, MarketPricingRules rules, Context context) {
        Objects.requireNonNull(rules); Objects.requireNonNull(previous); Objects.requireNonNull(lastSale);
        if (seedAsk.raw() <= 0 || seedBid.raw() <= 0 || reference.raw() <= 0 || surplus < 0 || now < 0)
            throw new IllegalArgumentException("invalid market price inputs");
        if (previous.isPresent()) {
            var before = previous.orElseThrow();
            if (now < before.reviewedAt()) throw new IllegalArgumentException("price policy time went backwards");
            if (!rules.enabled() || now - before.reviewedAt() < rules.reviewTicks()) return before;
        }
        var anchor = previous.map(Quote::ask).orElse(seedAsk);
        var ask = bounded(SellerPriceDiscovery.ask(anchor, surplus, context.market(),
                context.economics().map(ResourceEconomicValue::retained).orElse(FixedScalar.ZERO)), seedAsk, rules);
        if (rules.seedBounded()) ask = new FixedScalar(Math.max(ask.raw(), context.materialFloor().raw()));
        var referenceBid = rules.seedBounded() ? new FixedScalar(BigInteger.valueOf(reference.raw()).multiply(BigInteger.valueOf(seedBid.raw()))
                .divide(BigInteger.valueOf(seedAsk.raw())).longValueExact()) : reference;
        var goodsBid = bounded(scale(referenceBid, 1000L + context.shortagePermille()), seedBid, rules);
        var bid = constrainBid(context.economics().map(ResourceEconomicValue::acquisition)
                .orElseGet(() -> context.acquisition().map(quote -> goodsBid.plus(quote.delivery())).orElse(goodsBid)), context);
        // Publish the calculated target directly. Neither receipts nor elapsed
        // time are independent percentage increments or discounts.
        var recovery = SellerRecoveryPricing.select(ask, surplus, context.market(), context.recoveryCash());
        if (recovery.isPresent()) {
            var value = recovery.orElseThrow();
            return new Quote(value.price(), bid, reference, now, surplus, lastSale,
                    new StockSalePlan(StockSalePlan.Kind.OPERATING_RECOVERY, value.quantity(), context.recoveryCash()));
        }
        return new Quote(ask, bid, reference, now, surplus, lastSale, StockSalePlan.retained());
    }
    public Quote retainStock(Quote proposed, FixedScalar seed, MarketPricingRules rules, Context context) {
        var ask = bounded(SellerPriceDiscovery.ask(proposed.ask(), proposed.surplus(), context.market(),
                context.economics().map(ResourceEconomicValue::retained).orElse(FixedScalar.ZERO)), seed, rules);
        if (rules.seedBounded()) ask = new FixedScalar(Math.max(ask.raw(), context.materialFloor().raw()));
        return new Quote(ask, proposed.bid(), proposed.reference(), proposed.reviewedAt(), proposed.surplus(), proposed.lastSale(), StockSalePlan.retained());
    }
    private static FixedScalar constrainBid(FixedScalar value, Context context) {
        return context.alternativeCeiling().map(ceiling -> new FixedScalar(Math.min(value.raw(), ceiling.raw()))).orElse(value);
    }
    private static FixedScalar bounded(FixedScalar value, FixedScalar seed, MarketPricingRules rules) {
        if (!rules.seedBounded()) return value;
        return new FixedScalar(Math.max(scale(seed, rules.minimumSeedPermille()).raw(),
                Math.min(scale(seed, rules.maximumSeedPermille()).raw(), value.raw())));
    }
    private static FixedScalar scale(FixedScalar value, long permille) {
        return new FixedScalar(Math.max(1, BigInteger.valueOf(value.raw()).multiply(BigInteger.valueOf(permille))
                .divide(BigInteger.valueOf(1000)).min(BigInteger.valueOf(Long.MAX_VALUE)).longValueExact()));
    }
}

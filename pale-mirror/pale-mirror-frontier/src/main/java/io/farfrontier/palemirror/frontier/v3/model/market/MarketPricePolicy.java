package io.farfrontier.palemirror.frontier.v3.model.market;

import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.math.BigInteger;
import java.util.*;

/** Adaptive participant quotation, separate from market clearing and public needs planning. */
public final class MarketPricePolicy {
    public record Quote(FixedScalar ask, FixedScalar bid, FixedScalar reference, long reviewedAt,
                         long surplus, Optional<SubjectId> lastSale, long unsoldAccessibleTicks) {
        public Quote {
            Objects.requireNonNull(ask); Objects.requireNonNull(bid); Objects.requireNonNull(reference);
            lastSale = Objects.requireNonNull(lastSale);
            if (ask.raw() <= 0 || bid.raw() <= 0 || reference.raw() <= 0 || reviewedAt < 0 || surplus < 0 || unsoldAccessibleTicks < 0)
                throw new IllegalArgumentException("invalid retained quotation policy");
        }
    }
    public record Context(FixedScalar materialFloor, Optional<FixedScalar> alternativeCeiling,
                          int shortagePermille, boolean accessibleSurplus) {
        public Context {
            Objects.requireNonNull(materialFloor); Objects.requireNonNull(alternativeCeiling);
            if (materialFloor.raw() < 0 || alternativeCeiling.filter(v -> v.raw() <= 0).isPresent()
                    || shortagePermille < 0 || shortagePermille > 1000) throw new IllegalArgumentException("invalid economic price constraints");
        }
    }
    public Quote review(FixedScalar seedAsk, FixedScalar seedBid, FixedScalar reference, long surplus,
                        Optional<SubjectId> lastSale, Optional<Quote> previous, long now, MarketPricingRules rules) {
        return review(seedAsk, seedBid, reference, surplus, lastSale, previous, now, rules,
                new Context(FixedScalar.ZERO, Optional.empty(), 0, false));
    }
    public Quote review(FixedScalar seedAsk, FixedScalar seedBid, FixedScalar reference, long surplus,
                        Optional<SubjectId> lastSale, Optional<Quote> previous, long now, MarketPricingRules rules, Context context) {
        Objects.requireNonNull(rules); Objects.requireNonNull(previous); Objects.requireNonNull(lastSale);
        if (seedAsk.raw() <= 0 || seedBid.raw() <= 0 || reference.raw() <= 0 || surplus < 0 || now < 0)
            throw new IllegalArgumentException("invalid market price inputs");
        if (previous.isEmpty()) return new Quote(new FixedScalar(Math.max(seedAsk.raw(), context.materialFloor().raw())),
                constrainBid(seedBid, context), reference, now, surplus, lastSale, 0);
        var before = previous.orElseThrow();
        if (now < before.reviewedAt()) throw new IllegalArgumentException("price policy time went backwards");
        if (!rules.enabled() || now - before.reviewedAt() < rules.reviewTicks()) return before;
        boolean sold = lastSale.isPresent() && !lastSale.equals(before.lastSale());
        long unsold = !sold && surplus > 0 && context.accessibleSurplus()
                ? Math.min(rules.recencyTicks(), Math.addExact(before.unsoldAccessibleTicks(), now - before.reviewedAt())) : 0;
        // Time or route waiting alone is not a price signal. Stock accumulation,
        // accepted sales and published completed-price changes are explicit facts.
        int direction = sold ? 1 : surplus > before.surplus() || unsold >= rules.recencyTicks() ? -1 : reference.compareTo(before.reference());
        var ask = bounded(move(before.ask(), direction, rules.stepPermille()), seedAsk, rules);
        ask = new FixedScalar(Math.max(ask.raw(), context.materialFloor().raw()));
        var referenceBid = new FixedScalar(BigInteger.valueOf(reference.raw()).multiply(BigInteger.valueOf(seedBid.raw()))
                .divide(BigInteger.valueOf(seedAsk.raw())).longValueExact());
        var bidTarget = constrainBid(bounded(scale(referenceBid, 1000L + context.shortagePermille()), seedBid, rules), context);
        var bid = moveToward(before.bid(), bidTarget, rules.stepPermille());
        return new Quote(ask, bid, reference, now, surplus, lastSale, unsold);
    }
    private static FixedScalar constrainBid(FixedScalar value, Context context) {
        return context.alternativeCeiling().map(ceiling -> new FixedScalar(Math.min(value.raw(), ceiling.raw()))).orElse(value);
    }
    private static FixedScalar moveToward(FixedScalar before, FixedScalar target, int step) {
        var changed = move(before, target.compareTo(before), step);
        return target.compareTo(before) > 0 ? new FixedScalar(Math.min(changed.raw(), target.raw()))
                : new FixedScalar(Math.max(changed.raw(), target.raw()));
    }
    private static FixedScalar move(FixedScalar value, int direction, int step) {
        return direction == 0 ? value : scale(value, 1000L + (direction > 0 ? step : -step));
    }
    private static FixedScalar bounded(FixedScalar value, FixedScalar seed, MarketPricingRules rules) {
        return new FixedScalar(Math.max(scale(seed, rules.minimumSeedPermille()).raw(),
                Math.min(scale(seed, rules.maximumSeedPermille()).raw(), value.raw())));
    }
    private static FixedScalar scale(FixedScalar value, long permille) {
        return new FixedScalar(Math.max(1, BigInteger.valueOf(value.raw()).multiply(BigInteger.valueOf(permille))
                .divide(BigInteger.valueOf(1000)).longValueExact()));
    }
}

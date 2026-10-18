package io.farfrontier.palemirror.frontier.v3.model.market;

import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import java.math.BigInteger;
import java.util.Objects;

/** Resource expense is a quote; labour is an opportunity estimate, not wages
 * or a second monetary debit. The commercial owner alone reserves/settles cash. */
public record DeliveryCost(FixedScalar resourceExpense, long workerTicks) {
    public DeliveryCost {
        Objects.requireNonNull(resourceExpense);
        if (resourceExpense.raw() < 0 || workerTicks < 0) throw new IllegalArgumentException("negative delivery cost");
    }
    /** Fixed journey expense shared across the whole manifest by merchandise
     * value. Micro-unit rounding is disclosed in the accepted per-unit quote. */
    public FixedScalar unitCharge(FixedScalar goodsUnitPrice, FixedScalar manifestValue) {
        if (goodsUnitPrice.raw() <= 0 || manifestValue.raw() < goodsUnitPrice.raw())
            throw new IllegalArgumentException("invalid freight allocation basis");
        var numerator = BigInteger.valueOf(resourceExpense.raw()).multiply(BigInteger.valueOf(goodsUnitPrice.raw()));
        var denominator = BigInteger.valueOf(manifestValue.raw());
        return new FixedScalar(numerator.add(denominator).subtract(BigInteger.ONE).divide(denominator).longValueExact());
    }
}

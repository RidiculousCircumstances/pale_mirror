package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import java.util.Objects;

/** Constant-size account history since registration. Holds and budgets are not payments.
 * These totals explain balance changes, not profit or a commodity-specific trade history. */
public record FinancialTurnover(FixedScalar openingBalance, FixedScalar received, FixedScalar paid) {
    public FinancialTurnover {
        Objects.requireNonNull(openingBalance); Objects.requireNonNull(received); Objects.requireNonNull(paid);
        if (received.raw() < 0 || paid.raw() < 0) throw new IllegalArgumentException("negative account turnover");
    }
    public static FinancialTurnover opening(FixedScalar balance) {
        return new FinancialTurnover(balance, FixedScalar.ZERO, FixedScalar.ZERO);
    }
    public FixedScalar balance() { return openingBalance.plus(received.minus(paid)); }
    FinancialTurnover receive(FixedScalar amount) {
        return new FinancialTurnover(openingBalance, received.plus(amount), paid);
    }
    FinancialTurnover pay(FixedScalar amount) {
        return new FinancialTurnover(openingBalance, received, paid.plus(amount));
    }
}

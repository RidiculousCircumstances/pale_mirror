package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;

/** Derived fixed-size financial explanation; the ledger remains the only money authority. */
public record FinancialAccountReport(FinancialTurnover turnover, FixedScalar balance,
                                     FixedScalar creditLimit, FixedScalar reservedPayments,
                                     FixedScalar unusedBudgets, FixedScalar availableToCommit) {
    public static FinancialAccountReport read(EconomicLedger ledger, SubjectId owner) {
        var account = ledger.require(owner);
        var payments = ledger.reservations().values().stream().filter(value -> value.payerId().equals(owner))
                .map(FinancialReservation::amount).reduce(FixedScalar.ZERO, FixedScalar::plus);
        var budgets = ledger.budgets().values().stream().filter(value -> value.payerId().equals(owner))
                .map(FinancialBudget::remaining).reduce(FixedScalar.ZERO, FixedScalar::plus);
        return new FinancialAccountReport(account.turnover(), account.balance(), account.creditLimit(),
                payments, budgets, ledger.availableToReserve(owner));
    }
}

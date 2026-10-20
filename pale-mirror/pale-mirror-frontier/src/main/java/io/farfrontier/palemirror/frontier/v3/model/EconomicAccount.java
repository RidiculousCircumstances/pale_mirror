package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/** One exact financial account; physical items remain a separate exact-claim ledger. */
public record EconomicAccount(SubjectId ownerId, EconomicOwnerKind ownerKind, EconomicAccountStatus status,
                              FixedScalar balance, FixedScalar creditLimit, FinancialTurnover turnover) {
    /** Registration starts a new account, never reconstruction of an existing account's history. */
    public EconomicAccount(SubjectId ownerId, EconomicOwnerKind ownerKind, EconomicAccountStatus status,
                           FixedScalar balance, FixedScalar creditLimit) {
        this(ownerId, ownerKind, status, balance, creditLimit, FinancialTurnover.opening(balance));
    }
    public EconomicAccount {
        Objects.requireNonNull(ownerId, "account owner");
        Objects.requireNonNull(ownerKind, "account owner kind");
        Objects.requireNonNull(status, "account status");
        Objects.requireNonNull(balance, "account balance");
        Objects.requireNonNull(creditLimit, "account credit limit");
        Objects.requireNonNull(turnover, "account turnover");
        if (!turnover.balance().equals(balance)) throw new IllegalArgumentException("account balance contradicts its turnover");
        if (creditLimit.raw() < 0L) throw new IllegalArgumentException("account credit limit must not be negative");
        if (ownerKind == EconomicOwnerKind.RESIDENT && (balance.raw() != 0L || creditLimit.raw() != 0L
                || turnover.openingBalance().raw() != 0 || turnover.received().raw() != 0 || turnover.paid().raw() != 0))
            throw new IllegalArgumentException("resident title registration cannot hold money or credit");
    }

    EconomicAccount debit(FixedScalar amount) {
        if (amount.raw() <= 0L) throw new IllegalArgumentException("account debit must be positive");
        FixedScalar next = balance.minus(amount);
        if (status != EconomicAccountStatus.ACTIVE || next.raw() < -creditLimit.raw()) {
            throw new IllegalArgumentException("account debit exceeds available credit");
        }
        return new EconomicAccount(ownerId, ownerKind, status, next, creditLimit, turnover.pay(amount));
    }

    EconomicAccount credit(FixedScalar amount) {
        if (amount.raw() <= 0L) throw new IllegalArgumentException("account credit must be positive");
        // Insolvency blocks new obligations and outgoing payments.  It must not erase a
        // counterparty's already-owned claim: a creditor, court settlement or future
        // recapitalization can still pay this exact account without minting money.
        return new EconomicAccount(ownerId, ownerKind, status, balance.plus(amount), creditLimit, turnover.receive(amount));
    }
}

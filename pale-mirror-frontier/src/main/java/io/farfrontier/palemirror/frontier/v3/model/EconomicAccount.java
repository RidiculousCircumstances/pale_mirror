package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/** One exact financial account; physical items remain a separate exact-claim ledger. */
public record EconomicAccount(SubjectId ownerId, EconomicOwnerKind ownerKind, EconomicAccountStatus status,
                              FixedScalar balance, FixedScalar creditLimit) {
    public EconomicAccount {
        Objects.requireNonNull(ownerId, "account owner");
        Objects.requireNonNull(ownerKind, "account owner kind");
        Objects.requireNonNull(status, "account status");
        Objects.requireNonNull(balance, "account balance");
        Objects.requireNonNull(creditLimit, "account credit limit");
        if (creditLimit.raw() < 0L) throw new IllegalArgumentException("account credit limit must not be negative");
    }

    EconomicAccount debit(FixedScalar amount) {
        FixedScalar next = balance.minus(amount);
        if (status != EconomicAccountStatus.ACTIVE || next.raw() < -creditLimit.raw()) {
            throw new IllegalArgumentException("account debit exceeds available credit");
        }
        return new EconomicAccount(ownerId, ownerKind, status, next, creditLimit);
    }

    EconomicAccount credit(FixedScalar amount) {
        if (status != EconomicAccountStatus.ACTIVE) throw new IllegalArgumentException("insolvent account may not receive a new transfer");
        return new EconomicAccount(ownerId, ownerKind, status, balance.plus(amount), creditLimit);
    }
}

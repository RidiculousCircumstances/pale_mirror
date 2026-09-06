package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/**
 * One bounded, named hold on a payer's available funds.  It is not money and
 * cannot change either account balance until its owner settles it explicitly.
 */
public record FinancialReservation(SubjectId id, SubjectId payerId, SubjectId payeeId, SubjectId reasonId, FixedScalar amount) {
    public FinancialReservation {
        Objects.requireNonNull(id, "reservation id"); Objects.requireNonNull(payerId, "reservation payer");
        Objects.requireNonNull(payeeId, "reservation payee"); Objects.requireNonNull(reasonId, "reservation reason");
        Objects.requireNonNull(amount, "reservation amount");
        if (!id.value().startsWith("reservation:") || payerId.equals(payeeId) || amount.raw() <= 0L) {
            throw new IllegalArgumentException("financial reservation must have a named distinct counterparty and positive amount");
        }
    }
}

package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/** Durable zero-sum account transfer; resource custody is deliberately unaffected. */
public record EconomicTransfer(SubjectId payerId, SubjectId payeeId, FixedScalar amount, String reason) implements FrontierPayload {
    public EconomicTransfer {
        Objects.requireNonNull(payerId, "payer"); Objects.requireNonNull(payeeId, "payee"); Objects.requireNonNull(amount, "amount");
        if (payerId.equals(payeeId) || amount.raw() <= 0L) throw new IllegalArgumentException("transfer requires distinct accounts and a positive amount");
        if (reason == null || !reason.matches("[a-z][a-z0-9_.-]{0,63}")) throw new IllegalArgumentException("transfer reason must be a stable token");
    }
    @Override public String type() { return "frontier.economic_transfer"; }
}

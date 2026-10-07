package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.Objects;

/** An owned treasury hold before a supplier is known; not a balance or a new money account. */
public record FinancialBudget(SubjectId id, SubjectId payerId, OwnerKind ownerKind,
                              SubjectId ownerId, FixedScalar remaining) {
    public enum OwnerKind { TRANSPORT_MISSION }
    public FinancialBudget {
        Objects.requireNonNull(id); Objects.requireNonNull(payerId); Objects.requireNonNull(ownerKind);
        Objects.requireNonNull(ownerId); Objects.requireNonNull(remaining);
        if (remaining.raw() < 0) throw new IllegalArgumentException("negative financial budget");
    }
    public FinancialBudget withRemaining(FixedScalar amount) {
        return new FinancialBudget(id, payerId, ownerKind, ownerId, amount);
    }
}

package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.Objects;

/** Explicit permission to use an allocation without changing its claimant or title. */
public record ResourceClaimDelegation(Kind kind, SubjectId claimId, SubjectId claimantId,
                                      SubjectId executorId, long authorizationRevision) {
    public enum Kind { GOODS_CONTRACT_SHIPMENT }
    public ResourceClaimDelegation {
        Objects.requireNonNull(kind); Objects.requireNonNull(claimId); Objects.requireNonNull(claimantId);
        Objects.requireNonNull(executorId);
        if (claimantId.equals(executorId) || authorizationRevision < 0)
            throw new IllegalArgumentException("delegated claim use requires distinct exact owners and a revision");
    }
}

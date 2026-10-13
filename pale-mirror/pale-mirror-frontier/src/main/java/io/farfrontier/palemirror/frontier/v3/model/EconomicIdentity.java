package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.Objects;

/** Nominal economic actor declaration shared by workflows; no identity discovery. */
public record EconomicIdentity(SubjectId id, EconomicOwnerKind kind) {
    public EconomicIdentity { Objects.requireNonNull(id); Objects.requireNonNull(kind); }
    public void require(EconomicLedger ledger) {
        if (ledger.require(id).ownerKind() != kind || ledger.require(id).status() != EconomicAccountStatus.ACTIVE)
            throw new IllegalArgumentException("economic identity differs from its active declared account");
    }
}

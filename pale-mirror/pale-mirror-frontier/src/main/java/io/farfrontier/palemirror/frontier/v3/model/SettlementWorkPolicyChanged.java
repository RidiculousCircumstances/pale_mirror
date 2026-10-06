package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import java.util.Objects;

/** Exact compare-and-set policy event; live jobs keep their decision authority epoch. */
public record SettlementWorkPolicyChanged(SubjectId settlementId, long authorityEpoch, long assessedAtTick,
                                         ResidentWorkPermissions expected, ResidentWorkPermissions next)
        implements FrontierPayload {
    public SettlementWorkPolicyChanged {
        Objects.requireNonNull(settlementId); Objects.requireNonNull(expected); Objects.requireNonNull(next);
        if (authorityEpoch < 0 || assessedAtTick < 0 || expected.equals(next))
            throw new IllegalArgumentException("invalid or empty work policy transition");
    }
    @Override public String type() { return "frontier.settlement_work_policy_changed"; }
}

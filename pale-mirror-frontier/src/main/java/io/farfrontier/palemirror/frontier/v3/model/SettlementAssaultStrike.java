package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/** One exact COLD combat strike in a retained settlement assault. */
public record SettlementAssaultStrike(SubjectId assaultId, SubjectId attackerId, SubjectId targetId, int epoch,
                               FixedScalar damage) implements FrontierPayload {
    public SettlementAssaultStrike {
        Objects.requireNonNull(assaultId, "settlement assault"); Objects.requireNonNull(attackerId, "assault attacker");
        Objects.requireNonNull(targetId, "assault target"); Objects.requireNonNull(damage, "assault damage");
        if (epoch < 0 || damage.compareTo(FixedScalar.ZERO) <= 0) throw new IllegalArgumentException("invalid COLD assault strike");
    }
    @Override public String type() { return "frontier.settlement_assault_strike"; }
}

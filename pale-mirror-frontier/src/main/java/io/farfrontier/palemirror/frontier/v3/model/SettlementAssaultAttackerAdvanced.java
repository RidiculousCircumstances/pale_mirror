package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/** One exact attacker's one-step COLD advance toward the retained settlement anchor. */
record SettlementAssaultAttackerAdvanced(SubjectId assaultId, SubjectId attackerId, int routeIndex) implements FrontierPayload {
    SettlementAssaultAttackerAdvanced {
        Objects.requireNonNull(assaultId, "settlement assault"); Objects.requireNonNull(attackerId, "assault attacker");
        if (routeIndex < 1) throw new IllegalArgumentException("assault route cursor must advance");
    }
    @Override public String type() { return "frontier.settlement_assault_attacker_advanced"; }
}

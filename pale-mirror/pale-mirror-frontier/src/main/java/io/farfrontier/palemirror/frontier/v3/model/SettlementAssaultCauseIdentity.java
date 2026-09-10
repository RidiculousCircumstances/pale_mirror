package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/**
 * One semantic strike identity shared by COLD resolution and a HOT assault scene.
 * Presentation mode is intentionally absent: visibility may change the executor, never the
 * causal identity of the same attacker/epoch strike.
 */
public final class SettlementAssaultCauseIdentity {
    private SettlementAssaultCauseIdentity() { }

    public static SubjectId strike(SubjectId assaultId, SubjectId attackerId, long epoch) {
        Objects.requireNonNull(assaultId, "assault id"); Objects.requireNonNull(attackerId, "assault attacker");
        if (!assaultId.value().startsWith("assault:") || epoch < 0) throw new IllegalArgumentException("invalid settlement assault strike cause");
        return new SubjectId("cause:" + assaultId.value().substring("assault:".length()) + "-epoch-" + epoch
                + "-attacker-" + attackerId.value().replace(':', '-'));
    }

    public static SubjectId strike(SettlementAssaultSceneCause scene, SubjectId attackerId, long epoch) {
        Objects.requireNonNull(scene, "settlement assault scene");
        return strike(scene.assaultId(), attackerId, epoch);
    }
}

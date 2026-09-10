package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus;

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

    /**
     * A HOT lease consumes the canonical assault epoch directly.  Confirmation advances that
     * same retained history before a lease can release to COLD; an in-flight receipt therefore
     * deliberately retains its epoch until it is either confirmed or recovered.
     */
    public static long hotEpoch(SettlementAssault assault, Iterable<PhysicalIntent> intents) {
        Objects.requireNonNull(assault, "settlement assault");
        Objects.requireNonNull(intents, "physical intents");
        for (PhysicalIntent intent : intents) {
            if (intent.kind() == PhysicalIntentKind.SCENE_STRIKE && intent.status() != PhysicalIntentStatus.CONFIRMED
                    && belongsTo(assault.id(), intent.causeSubjectId()) && epoch(assault.id(), intent.causeSubjectId()) != assault.nextStrikeEpoch()) {
                throw new IllegalArgumentException("in-flight settlement assault strike does not retain the current epoch");
            }
        }
        return assault.nextStrikeEpoch();
    }

    public static boolean belongsTo(SubjectId assaultId, SubjectId causeId) {
        Objects.requireNonNull(assaultId, "settlement assault"); Objects.requireNonNull(causeId, "settlement assault cause");
        if (!assaultId.value().startsWith("assault:")) return false;
        return causeId.value().startsWith("cause:" + assaultId.value().substring("assault:".length()) + "-epoch-");
    }

    /** Returns the exact durable strike epoch encoded by {@link #strike}. */
    public static long epoch(SubjectId assaultId, SubjectId causeId) {
        if (!belongsTo(assaultId, causeId)) throw new IllegalArgumentException("cause does not belong to settlement assault");
        String prefix = "cause:" + assaultId.value().substring("assault:".length()) + "-epoch-";
        String encoded = causeId.value().substring(prefix.length());
        int attacker = encoded.indexOf("-attacker-");
        if (attacker <= 0 || attacker + "-attacker-".length() == encoded.length()) {
            throw new IllegalArgumentException("invalid settlement assault strike cause");
        }
        try {
            long epoch = Long.parseLong(encoded.substring(0, attacker));
            if (epoch < 0) throw new IllegalArgumentException("invalid settlement assault strike cause");
            return epoch;
        } catch (NumberFormatException invalid) {
            throw new IllegalArgumentException("invalid settlement assault strike cause", invalid);
        }
    }
}

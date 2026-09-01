package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/**
 * One observed HOT Scout rebase for a deployed lease with an obsolete patrol target.
 *
 * <p>The observation is deliberately narrower than an ordinary patrol advance: it records the
 * old canonical cursor, the exact owned body's observed lease target (which may still equal the
 * old cursor), and the only subsequent perimeter target.  It is therefore a durable
 * reconciliation of a real body, not permission to reposition a Scout or select a new route.</p>
 */
public record ScoutPatrolLeaseRecovered(SubjectId scoutId, BlockPosition priorCanonicalPosition,
                                        BlockPosition observedLeasePosition, BlockPosition nextGoalPosition) implements FrontierPayload {
    public ScoutPatrolLeaseRecovered {
        Objects.requireNonNull(scoutId, "scout patrol recovery scout");
        Objects.requireNonNull(priorCanonicalPosition, "scout patrol recovery prior position");
        Objects.requireNonNull(observedLeasePosition, "scout patrol recovery observed position");
        Objects.requireNonNull(nextGoalPosition, "scout patrol recovery next goal");
    }

    @Override public String type() { return "frontier.scout_patrol_lease_recovered"; }
}

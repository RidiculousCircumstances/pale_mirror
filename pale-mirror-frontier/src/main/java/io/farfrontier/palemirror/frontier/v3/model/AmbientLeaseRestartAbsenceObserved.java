package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/**
 * Loaded-world evidence that an exact ambient body was absent after a server restart.
 *
 * <p>This is not death evidence.  It closes only the unknown physical lease while
 * retaining the living canonical actor at its exact hand-off position; a later normal
 * demand may prepare and materialize that actor again.</p>
 */
public record AmbientLeaseRestartAbsenceObserved(SubjectId actorId, BodyPosition body) implements FrontierPayload {
    public AmbientLeaseRestartAbsenceObserved {
        Objects.requireNonNull(actorId, "actor id");
        Objects.requireNonNull(body, "body");
    }

    @Override public String type() { return "frontier.ambient_lease_restart_absence_observed"; }
}

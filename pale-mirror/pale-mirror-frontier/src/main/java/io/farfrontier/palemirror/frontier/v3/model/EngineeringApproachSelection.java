package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.navigation.TraversalRejoin;
import java.util.Objects;

/** Selected approach to one unchanged crew checkpoint; never a new task or body position. */
public record EngineeringApproachSelection(SubjectId actorId, StationApproachState predecessor, TraversalRejoin approach) {
    public EngineeringApproachSelection {
        Objects.requireNonNull(actorId); Objects.requireNonNull(predecessor); Objects.requireNonNull(approach);
        if (predecessor.waitingOrigin().isEmpty() || approach.cursor() != 0
                || !predecessor.waitingOrigin().orElseThrow().equals(approach.current()))
            throw new IllegalArgumentException("engineering approach lacks its exact saved waiting origin");
    }
}

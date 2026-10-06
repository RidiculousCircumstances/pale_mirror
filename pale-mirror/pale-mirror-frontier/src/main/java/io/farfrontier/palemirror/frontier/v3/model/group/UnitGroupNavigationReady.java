package io.farfrontier.palemirror.frontier.v3.model.group;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.Objects;

/** Calculation notification only: the group owner rechecks authority before any movement. */
public record UnitGroupNavigationReady(SubjectId groupId, long expectedRevision) implements FrontierPayload {
    public UnitGroupNavigationReady {
        Objects.requireNonNull(groupId);
        if (expectedRevision < 1) throw new IllegalArgumentException("navigation notification lacks group revision");
    }
    @Override public String type() { return "frontier.unit_group_navigation_ready"; }
}

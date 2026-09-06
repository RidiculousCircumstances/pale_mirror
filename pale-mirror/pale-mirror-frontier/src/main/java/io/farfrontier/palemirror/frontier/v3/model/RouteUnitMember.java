package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/** One named resident and one bounded duty within a route owner's retained unit. */
public record RouteUnitMember(SubjectId residentId, RouteUnitDuty duty) {
    public RouteUnitMember {
        Objects.requireNonNull(residentId, "route unit resident");
        Objects.requireNonNull(duty, "route unit duty");
        if (!residentId.value().startsWith("resident:")) throw new IllegalArgumentException("route unit member must be a resident");
    }
}

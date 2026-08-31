package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

public record RoutePatrolObstructionConfirmed(SubjectId taskId, BlockPosition position) implements FrontierPayload {
    public RoutePatrolObstructionConfirmed { Objects.requireNonNull(taskId, "patrol task"); Objects.requireNonNull(position, "obstruction position"); }
    @Override public String type() { return "frontier.route_patrol_obstruction_confirmed"; }
}

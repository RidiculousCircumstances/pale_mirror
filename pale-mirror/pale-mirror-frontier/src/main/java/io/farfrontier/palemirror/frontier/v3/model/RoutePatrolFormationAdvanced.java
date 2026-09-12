package io.farfrontier.palemirror.frontier.v3.model;
import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.Objects;
/** Canonical COLD formation edge; no physical body coordinate is fabricated. */
public record RoutePatrolFormationAdvanced(SubjectId taskId) implements FrontierPayload {
    public RoutePatrolFormationAdvanced { Objects.requireNonNull(taskId, "patrol task"); }
    @Override public String type() { return "frontier.route_patrol_formation_advanced"; }
}

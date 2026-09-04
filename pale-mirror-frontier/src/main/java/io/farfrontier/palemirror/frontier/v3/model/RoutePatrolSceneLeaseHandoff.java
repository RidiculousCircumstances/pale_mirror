package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;

import java.util.List;
import java.util.Objects;

/** Durable transfer of retained ambient patrol bodies into their route-patrol scene. */
public record RoutePatrolSceneLeaseHandoff(SceneLease lease, List<SceneMemberPosition> ambientMembers) implements FrontierPayload {
    public RoutePatrolSceneLeaseHandoff {
        Objects.requireNonNull(lease, "route-patrol lease");
        ambientMembers = List.copyOf(Objects.requireNonNull(ambientMembers, "route-patrol ambient members"));
        if (!FrontierSceneBehaviors.isRoutePatrol(lease)) throw new IllegalArgumentException("route-patrol hand-off requires its typed cause");
    }

    @Override public String type() { return "frontier.route_patrol_scene_lease_handoff"; }
    @Override public boolean requiresDurableBeforeEffect() { return true; }
}
